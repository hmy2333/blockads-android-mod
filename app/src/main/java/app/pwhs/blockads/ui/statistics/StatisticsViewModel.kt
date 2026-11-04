package app.pwhs.blockads.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.blockads.data.dao.DnsLogDao
import app.pwhs.blockads.data.dao.FilterListDao
import app.pwhs.blockads.data.entities.AppStat
import app.pwhs.blockads.data.entities.BlockReasonStat
import app.pwhs.blockads.data.entities.CountryStat
import app.pwhs.blockads.data.entities.CountryTopDomain
import app.pwhs.blockads.data.entities.DailyStat
import app.pwhs.blockads.data.entities.HourlyStat
import app.pwhs.blockads.data.entities.MonthlyStat
import app.pwhs.blockads.data.entities.TopBlockedDomain
import app.pwhs.blockads.data.entities.WeeklyStat
import app.pwhs.blockads.data.repository.FilterListRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

class StatisticsViewModel(
    dnsLogDao: DnsLogDao,
    filterListDao: FilterListDao,
    clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val todayStart: Long = Calendar.getInstance().apply {
        timeInMillis = clock()
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val _destinationTimeRange = MutableStateFlow(DestinationTimeRange.HOURS_24)
    val destinationTimeRange: StateFlow<DestinationTimeRange> = _destinationTimeRange.asStateFlow()

    private val _selectedCountryIso = MutableStateFlow<String?>(null)
    val selectedCountryIso: StateFlow<String?> = _selectedCountryIso.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val countryStats: StateFlow<List<CountryStat>> = _destinationTimeRange.flatMapLatest { range ->
        if (range == DestinationTimeRange.ALL) {
            dnsLogDao.getAllCountryStats()
        } else {
            val since = clock() - range.hours * 3600_000L
            dnsLogDao.getCountryStatsSince(since)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val countryTopDomains: StateFlow<List<CountryTopDomain>> = combine(
        _destinationTimeRange,
        _selectedCountryIso
    ) { range, iso ->
        range to iso
    }.flatMapLatest { (range, iso) ->
        if (iso.isNullOrBlank()) {
            flowOf(emptyList())
        } else if (range == DestinationTimeRange.ALL) {
            dnsLogDao.getAllCountryTopDomains(iso)
        } else {
            val since = clock() - range.hours * 3600_000L
            dnsLogDao.getCountryTopDomainsSince(iso, since)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDestinationTimeRange(range: DestinationTimeRange) {
        _destinationTimeRange.value = range
    }

    fun selectCountry(iso: String?) {
        _selectedCountryIso.value = iso
    }

    private val _blockReasonTimeRange = MutableStateFlow(DestinationTimeRange.HOURS_24)
    val blockReasonTimeRange: StateFlow<DestinationTimeRange> = _blockReasonTimeRange.asStateFlow()

    fun setBlockReasonTimeRange(range: DestinationTimeRange) {
        _blockReasonTimeRange.value = range
    }

    private val allFiltersFlow = filterListDao.getAll()

    @OptIn(ExperimentalCoroutinesApi::class)
    val blockReasons: StateFlow<List<BlockReasonStat>> = combine(
        _blockReasonTimeRange.flatMapLatest { range ->
            if (range == DestinationTimeRange.ALL) {
                dnsLogDao.getAllBlockReasonStats()
            } else {
                val since = clock() - range.hours * 3600_000L
                dnsLogDao.getBlockReasonStatsSince(since)
            }
        },
        allFiltersFlow
    ) { rawStats, filterLists ->
        val filterMap = filterLists.associateBy { it.id.toString() }
        val aggregated = mutableMapOf<String, Int>()

        for (raw in rawStats) {
            val parts = raw.blockedBy.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue
            for (part in parts) {
                aggregated[part] = (aggregated[part] ?: 0) + raw.count
            }
        }

        aggregated.map { (key, count) ->
            val filter = filterMap[key]
            if (filter != null) {
                BlockReasonStat(
                    reasonKey = key,
                    displayName = filter.name,
                    count = count,
                    filterId = filter.id,
                    category = filter.category
                )
            } else {
                val (name, cat) = when (key.lowercase()) {
                    "custom", "custom_rule" -> "Custom Rules" to "CUSTOM"
                    "security" -> "Security Protection" to "SECURITY"
                    "firewall", "connection" -> "App Firewall" to "FIREWALL"
                    "upstream_dns" -> "Upstream DNS" to "DNS"
                    "filter_list", "ad" -> "Ad & Tracker Filter" to "AD"
                    else -> key to ""
                }
                BlockReasonStat(
                    reasonKey = key,
                    displayName = name,
                    count = count,
                    filterId = null,
                    category = cat
                )
            }
        }.sortedByDescending { it.count }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> = dnsLogDao.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val blockedCount: StateFlow<Int> = dnsLogDao.getBlockedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayTotal: StateFlow<Int> = dnsLogDao.getTotalCountSince(todayStart)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayBlocked: StateFlow<Int> = dnsLogDao.getBlockedCountSince(todayStart)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hourlyStats: StateFlow<List<HourlyStat>> = dnsLogDao.getHourlyStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyStats: StateFlow<List<DailyStat>> = dnsLogDao.getDailyStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklyStats: StateFlow<List<WeeklyStat>> = dnsLogDao.getWeeklyStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyStats: StateFlow<List<MonthlyStat>> = dnsLogDao.getMonthlyStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topBlockedDomains: StateFlow<List<TopBlockedDomain>> = dnsLogDao.getTopBlockedDomains()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topApps: StateFlow<List<AppStat>> = dnsLogDao.getTopApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
