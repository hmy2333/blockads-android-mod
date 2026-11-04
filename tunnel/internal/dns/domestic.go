package dns

import "strings"

// domesticSuffixes marks domains that must be resolved from inside China:
// asking an overseas resolver returns a foreign CDN edge, which then defeats
// the IP-based split (the connection would be routed abroad for no reason).
//
// This is a suffix table, not a geosite database: it covers the sites that
// actually matter for latency, and everything unmatched simply goes through the
// proxy resolver, which is the safe direction.
var domesticSuffixes = []string{
	"cn", "xn--fiqs8s", "xn--fiqz9s", // .cn / .中国 / .中國
	"com.cn", "net.cn", "org.cn", "gov.cn",

	"baidu.com", "bdstatic.com", "bdimg.com", "baidupcs.com",
	"qq.com", "qpic.cn", "qlogo.cn", "gtimg.cn", "qcloud.com", "tencent.com", "tencentcloudapi.com",
	"weixin.qq.com", "wechat.com",
	"taobao.com", "tmall.com", "alicdn.com", "aliyun.com", "aliyuncs.com", "alipay.com", "alibaba.com",
	"jd.com", "360buyimg.com", "jdcloud.com",
	"bilibili.com", "hdslb.com", "biliapi.net", "bilivideo.com",
	"zhihu.com", "zhimg.com", "zhihu.com",
	"weibo.com", "sina.com.cn", "sinaimg.cn", "weibo.cn",
	"163.com", "126.net", "127.net", "netease.com", "music.163.com",
	"douyin.com", "bytedance.com", "ixigua.com", "feishu.cn", "douyinpic.com",
	"kuaishou.com", "gifshow.com",
	"xiaomi.com", "mi.com", "miui.com", "huawei.com", "hicloud.com", "vivo.com.cn", "oppo.com",
	"meituan.com", "dianping.com", "sankuai.com",
	"pinduoduo.com", "yangkeduo.com", "pddpic.com",
	"ctrip.com", "trip.com.cn", "qunar.com", "12306.cn",
	"iqiyi.com", "qiyipic.com", "youku.com", "mgtv.com", "douban.com", "doubanio.com",
	"csdn.net", "cnblogs.com", "gitee.com", "oschina.net", "jianshu.com",
	"sohu.com", "ifeng.com", "people.com.cn", "xinhuanet.com", "cctv.com", "chinadaily.com.cn",
	"alipayobjects.com", "amap.com", "autonavi.com", "gaode.com",
	"kugou.com", "kuwo.cn", "xiami.com",
	"zol.com.cn", "pconline.com.cn", "cnzz.com", "51.la",
	"umeng.com", "getui.com", "jpush.cn", "xgpush.com", "cn.miui.com",
}

// IsDomesticDomain reports whether a domain belongs to the in-China resolution group.
func IsDomesticDomain(domain string) bool {
	name := strings.TrimSuffix(strings.ToLower(strings.TrimSpace(domain)), ".")
	if name == "" {
		return false
	}
	for _, suffix := range domesticSuffixes {
		if name == suffix || strings.HasSuffix(name, "."+suffix) {
			return true
		}
	}
	return false
}

// questionName extracts the QNAME from a raw query without unpacking the whole
// message, since this sits on the DNS fast path.
func questionName(raw []byte) string {
	if len(raw) < 13 {
		return ""
	}
	var b strings.Builder
	for i := 12; i < len(raw); {
		labelLen := int(raw[i])
		i++
		if labelLen == 0 {
			break
		}
		if labelLen&0xC0 != 0 || i+labelLen > len(raw) {
			return ""
		}
		if b.Len() > 0 {
			b.WriteByte('.')
		}
		b.Write(raw[i : i+labelLen])
		i += labelLen
	}
	return strings.ToLower(b.String())
}
