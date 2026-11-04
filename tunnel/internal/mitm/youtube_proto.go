package mitm

import (
	"bytes"
	"encoding/binary"
)

// ─────────────────────────────────────────────────────────────────────────────
// youtube_proto.go — Low-level Protobuf wire-format parser & cleaner for YouTube.
// ─────────────────────────────────────────────────────────────────────────────

var (
	bgRenderer = []byte{0x5a, 0x08, 0xf2, 0xf4, 0xd2, 0xf6, 0x01, 0x02, 0x08, 0x01}
	pipRenderer = []byte{
		0xaa, 0x01, 0x0e, 0xf2, 0xd6, 0xb8, 0xc2, 0x04,
		0x08, 0x08, 0x01, 0x20, 0x00, 0x30, 0x00, 0x40, 0x01,
	}
	bgSettingItem = []byte{
		0x32, 0x14, 0xc2, 0x9f, 0xc2, 0xd1, 0x02, 0x0d,
		0x10, 0x01, 0x18, 0x01, 0x48, 0x01, 0x50, 0x01,
		0x72, 0x03, 0x08, 0xc5, 0x08,
	}
	patIsAd = []byte{0x82, 0x01, 0x02, 0x08, 0x01}

	shortsAdPatterns = [][]byte{
		[]byte("pagead"),
		[]byte("adClientParams"),
		[]byte("adLayoutLoggingData"),
		[]byte("brandInteraction"),
		[]byte("reelAdRenderer"),
		[]byte("visitAdvertiser"),
		[]byte("ad_cpn"),
		[]byte("adPlacement"),
		[]byte("Sponsored"),
		[]byte("promoted"),
		[]byte("isAd"),
		[]byte("is_ad"),
		patIsAd,
	}

	surveyPatterns = [][]byte{
		[]byte("_survey"),
		[]byte("compact_survey"),
		[]byte("survey.eml"),
	}

	feedAdPatterns = [][]byte{
		[]byte("pagead"),
		[]byte("adClientParams"),
		[]byte("adLayoutLoggingData"),
		[]byte("brandInteraction"),
		[]byte("visitAdvertiser"),
		[]byte("ad_cpn"),
		[]byte("adPlacement"),
		[]byte("Sponsored"),
		[]byte("promoted"),
		[]byte("_survey"),
		[]byte("compact_survey"),
		[]byte("survey.eml"),
	}
)

const (
	elField   = 153515154 // elementRenderer
	minAdSize = 200
)

type protoField struct {
	field    int
	wireType int
	start    int
	end      int
	valStart int
}

func walkProto(b []byte, off, end int) []protoField {
	if off < 0 || end > len(b) || off > end {
		return nil
	}
	var fields []protoField
	i := off
	for i < end {
		s := i
		tag, n := binary.Uvarint(b[i:end])
		if n <= 0 {
			return nil
		}
		i += n
		fNum := int(tag >> 3)
		wt := int(tag & 0x7)
		switch wt {
		case 0:
			_, vn := binary.Uvarint(b[i:end])
			if vn <= 0 {
				return nil
			}
			i += vn
			fields = append(fields, protoField{field: fNum, wireType: wt, start: s, end: i, valStart: -1})
		case 1:
			i += 8
			if i > end {
				return nil
			}
			fields = append(fields, protoField{field: fNum, wireType: wt, start: s, end: i, valStart: -1})
		case 2:
			length, ln := binary.Uvarint(b[i:end])
			if ln <= 0 {
				return nil
			}
			i += ln
			vs := i
			i += int(length)
			if i > end || i < vs {
				return nil
			}
			fields = append(fields, protoField{field: fNum, wireType: wt, start: s, end: i, valStart: vs})
		case 5:
			i += 4
			if i > end {
				return nil
			}
			fields = append(fields, protoField{field: fNum, wireType: wt, start: s, end: i, valStart: -1})
		default:
			return nil
		}
	}
	if i != end {
		return nil
	}
	return fields
}

func encodeVarint(n uint64) []byte {
	var buf [binary.MaxVarintLen64]byte
	m := binary.PutUvarint(buf[:], n)
	return buf[:m]
}

func concatBytes(parts [][]byte) []byte {
	total := 0
	for _, p := range parts {
		total += len(p)
	}
	out := make([]byte, total)
	o := 0
	for _, p := range parts {
		copy(out[o:], p)
		o += len(p)
	}
	return out
}

func patchPlayability(b []byte) []byte {
	w := walkProto(b, 0, len(b))
	if w == nil {
		return b
	}
	var parts [][]byte
	for _, f := range w {
		if f.field == 11 || f.field == 21 {
			continue
		}
		parts = append(parts, b[f.start:f.end])
	}
	parts = append(parts, bgRenderer, pipRenderer)
	return concatBytes(parts)
}

func cleanPlayer(b []byte) []byte {
	w := walkProto(b, 0, len(b))
	if w == nil {
		return b
	}
	var parts [][]byte
	modified := false
	for _, f := range w {
		if f.field == 7 || f.field == 68 {
			modified = true
			continue
		}
		if f.field == 2 && f.wireType == 2 && f.valStart >= 0 {
			patched := patchPlayability(b[f.valStart:f.end])
			modified = true
			parts = append(parts, encodeVarint(uint64(f.field<<3|2)), encodeVarint(uint64(len(patched))), patched)
			continue
		}
		parts = append(parts, b[f.start:f.end])
	}
	if modified {
		return concatBytes(parts)
	}
	return b
}

func cleanGetWatch(b []byte) []byte {
	w := walkProto(b, 0, len(b))
	if w == nil {
		return b
	}
	var parts [][]byte
	modified := false
	for _, f := range w {
		if f.field == 1 && f.wireType == 2 && f.valStart >= 0 {
			cw := walkProto(b, f.valStart, f.end)
			if cw != nil {
				var cparts [][]byte
				for _, cf := range cw {
					if cf.field == 2 && cf.wireType == 2 && cf.valStart >= 0 {
						cleanP := cleanPlayer(b[cf.valStart:cf.end])
						if len(cleanP) != cf.end-cf.valStart {
							modified = true
							cparts = append(cparts, encodeVarint(uint64(cf.field<<3|2)), encodeVarint(uint64(len(cleanP))), cleanP)
							continue
						}
					} else if cf.wireType == 2 && cf.valStart >= 0 {
						pruned, rem := pruneProto(b, cf.valStart, cf.end)
						if rem > 0 {
							modified = true
							cparts = append(cparts, encodeVarint(uint64(cf.field<<3|2)), encodeVarint(uint64(len(pruned))), pruned)
							continue
						}
					}
					cparts = append(cparts, b[cf.start:cf.end])
				}
				if modified {
					cleanContent := concatBytes(cparts)
					parts = append(parts, encodeVarint(uint64(f.field<<3|2)), encodeVarint(uint64(len(cleanContent))), cleanContent)
					continue
				}
			}
		}
		parts = append(parts, b[f.start:f.end])
	}
	if modified {
		return concatBytes(parts)
	}
	return b
}

func dropShortsAds(b []byte) []byte {
	w := walkProto(b, 0, len(b))
	if w == nil {
		return b
	}
	var parts [][]byte
	removed := 0
	for _, f := range w {
		if (f.field == 1 || f.field == 2) && f.wireType == 2 && f.valStart >= 0 {
			if isShortsAd(b, f.valStart, f.end) {
				removed++
				continue
			}
		}
		parts = append(parts, b[f.start:f.end])
	}
	if removed > 0 {
		return concatBytes(parts)
	}
	return b
}

func isShortsAd(b []byte, s, e int) bool {
	sub := b[s:e]
	for _, pat := range shortsAdPatterns {
		if bytes.Contains(sub, pat) {
			return true
		}
	}
	w := walkProto(b, s, e)
	if w == nil {
		return false
	}
	for _, f := range w {
		if (f.field == 1 || f.field == 2 || f.field == 139608561) && f.wireType == 2 && f.valStart >= 0 {
			if f.field == 139608561 && checkReelWatchEndpointForAd(b, f.valStart, f.end) {
				return true
			}
			cw := walkProto(b, f.valStart, f.end)
			if cw != nil {
				for _, cf := range cw {
					if cf.field == 139608561 && cf.wireType == 2 && cf.valStart >= 0 {
						if checkReelWatchEndpointForAd(b, cf.valStart, cf.end) {
							return true
						}
					}
				}
			}
		}
	}
	return false
}

func checkReelWatchEndpointForAd(b []byte, s, e int) bool {
	sub := b[s:e]
	if bytes.Contains(sub, patIsAd) {
		return true
	}
	rw := walkProto(b, s, e)
	if rw == nil {
		return false
	}
	for _, rf := range rw {
		if rf.field == 16 && rf.wireType == 2 && rf.valStart >= 0 {
			aw := walkProto(b, rf.valStart, rf.end)
			if aw != nil {
				for _, af := range aw {
					if af.field == 1 && af.wireType == 0 {
						v, _ := binary.Uvarint(b[af.start+1 : af.end])
						if v == 1 {
							return true
						}
					}
				}
			}
		}
	}
	return false
}

func isAdOrSurveyElement(b []byte, s, e int) bool {
	sub := b[s:e]
	for _, pat := range feedAdPatterns {
		if bytes.Contains(sub, pat) {
			return true
		}
	}
	return false
}

func hasNestedElement(b []byte, s, e int) bool {
	w := walkProto(b, s, e)
	if w == nil {
		return false
	}
	for _, f := range w {
		if f.wireType == 2 && f.valStart >= 0 {
			if f.field == elField {
				return true
			}
			if hasNestedElement(b, f.valStart, f.end) {
				return true
			}
		}
	}
	return false
}

func pruneProto(b []byte, off, end int) ([]byte, int) {
	w := walkProto(b, off, end)
	if w == nil {
		return b[off:end], 0
	}
	var parts [][]byte
	removed := 0
	for _, f := range w {
		if f.wireType == 2 && f.valStart >= 0 {
			if f.field == elField && (f.end-f.start >= minAdSize) &&
				isAdOrSurveyElement(b, f.valStart, f.end) &&
				!hasNestedElement(b, f.valStart, f.end) {
				removed++
				continue
			}
			isMsg := walkProto(b, f.valStart, f.end)
			if isMsg != nil {
				childBuf, childRem := pruneProto(b, f.valStart, f.end)
				if childRem > 0 {
					removed += childRem
					parts = append(parts, encodeVarint(uint64(f.field<<3|2)), encodeVarint(uint64(len(childBuf))), childBuf)
					continue
				}
			}
		}
		parts = append(parts, b[f.start:f.end])
	}
	if removed > 0 {
		return concatBytes(parts), removed
	}
	return b[off:end], 0
}
