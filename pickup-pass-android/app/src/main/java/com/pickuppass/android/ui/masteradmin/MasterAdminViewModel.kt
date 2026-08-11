package com.pickuppass.android.ui.masteradmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.MasterPlanDefinition
import com.pickuppass.android.data.model.MasterSchoolItem
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.MasterAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MasterAdminUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val totalSchools: Int = 0,
    val activeSchools: Int = 0,
    val suspendedSchools: Int = 0,
    val schools: List<MasterSchoolItem> = emptyList(),
    val plans: Map<String, MasterPlanDefinition> = emptyMap(),
    val featureKeys: List<String> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class MasterAdminViewModel @Inject constructor(
    private val repository: MasterAdminRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MasterAdminUiState())
    val uiState: StateFlow<MasterAdminUiState> = _uiState

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        val plansResult = repository.getPlanCatalog()
        val schoolsResult = repository.listSchools()
        when (schoolsResult) {
            is ApiResult.Success -> {
                val planData = when (plansResult) {
                    is ApiResult.Success -> plansResult.data
                    is ApiResult.Failure -> null
                }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    totalSchools = schoolsResult.data.totalSchools,
                    activeSchools = schoolsResult.data.activeSchools,
                    suspendedSchools = schoolsResult.data.suspendedSchools,
                    schools = schoolsResult.data.schools,
                    plans = planData?.plans ?: emptyMap(),
                    featureKeys = planData?.featureKeys ?: emptyList(),
                    error = if (plansResult is ApiResult.Failure) plansResult.message else null
                )
            }
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(loading = false, error = schoolsResult.message)
        }
    }

    fun createSchool(name: String) = runSave("School created with a 30-day trial") { repository.createSchool(name.trim()) }

    fun setSchoolActive(schoolId: String, active: Boolean) =
        runSave(if (active) "School reactivated" else "School suspended") { repository.setSchoolActive(schoolId, active) }

    fun createSchoolAdmin(schoolId: String, email: String, lastName: String, firstName: String, middleInitial: String, suffix: String) =
        runSave("School administrator created") { repository.createSchoolAdmin(schoolId, email, lastName, firstName, middleInitial, suffix) }

    fun updateSubscription(
        schoolId: String,
        plan: String,
        subscriptionStatus: String,
        featureOverrides: Map<String, Boolean>,
        autoRenew: Boolean,
        cancelAtPeriodEnd: Boolean,
        startNewPeriod: Boolean,
        extendTrialDays: Int
    ) = runSave("Subscription and billing period updated") {
        repository.updateSubscription(
            schoolId, plan, subscriptionStatus, featureOverrides,
            autoRenew, cancelAtPeriodEnd, startNewPeriod, extendTrialDays
        )
    }

    fun reconcileSubscription(schoolId: String) =
        runSave("Subscription lifecycle checked") { repository.reconcileSubscription(schoolId) }

    fun signOut() { authRepository.signOut() }

    private fun runSave(message: String, block: suspend () -> ApiResult<*>) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null, message = null)
            when (val r = block()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(saving = false, message = message)
                    load()
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(saving = false, error = r.message)
            }
        }
    }
}
