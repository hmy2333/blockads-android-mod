package app.pwhs.blockads.ui.browser.elementrules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.blockads.R
import app.pwhs.blockads.data.dao.ElementRuleDao
import app.pwhs.blockads.data.entities.ElementRule
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ElementRulesViewModel(
    application: Application,
    private val elementRuleDao: ElementRuleDao
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ElementRulesUiState(isLoading = true))
    val uiState: StateFlow<ElementRulesUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<ElementRulesUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    private val searchQueryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            combine(
                elementRuleDao.getAllRules(),
                searchQueryFlow
            ) { allRules, query ->
                val filtered = if (query.isBlank()) {
                    allRules
                } else {
                    allRules.filter {
                        it.domain.contains(query, ignoreCase = true) ||
                                it.cssSelector.contains(query, ignoreCase = true)
                    }
                }
                val grouped = filtered.groupBy { it.domain }
                ElementRulesUiState(
                    rules = filtered,
                    rulesByDomain = grouped,
                    totalCount = allRules.size,
                    isLoading = false,
                    searchQuery = query
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun processIntent(intent: ElementRulesUiIntent) {
        when (intent) {
            is ElementRulesUiIntent.DeleteRule -> {
                viewModelScope.launch {
                    elementRuleDao.deleteById(intent.id)
                    _uiEffect.send(
                        ElementRulesUiEffect.ShowToast(
                            getApplication<Application>().getString(R.string.element_rules_toast_deleted)
                        )
                    )
                }
            }
            is ElementRulesUiIntent.DeleteAllForDomain -> {
                viewModelScope.launch {
                    elementRuleDao.deleteAllForDomain(intent.domain)
                    _uiEffect.send(
                        ElementRulesUiEffect.ShowToast(
                            getApplication<Application>().getString(R.string.element_rules_toast_deleted_all, intent.domain)
                        )
                    )
                }
            }
            is ElementRulesUiIntent.SearchQueryChanged -> {
                searchQueryFlow.value = intent.query
            }
        }
    }
}
