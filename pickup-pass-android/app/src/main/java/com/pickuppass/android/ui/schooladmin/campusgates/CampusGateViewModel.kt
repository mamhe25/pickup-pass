package com.pickuppass.android.ui.schooladmin.campusgates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.CampusItem
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import com.pickuppass.android.data.repository.SchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CampusGateUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val campuses: List<CampusItem> = emptyList(),
    val gates: List<PickupGateItem> = emptyList(),
    val multiCampusEnabled: Boolean = true,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class CampusGateViewModel @Inject constructor(
    private val repository: SchoolAdminRepository,
    private val schoolRepository: SchoolRepository
): ViewModel() {
    private val _uiState = MutableStateFlow(CampusGateUiState())
    val uiState: StateFlow<CampusGateUiState> = _uiState
    init { load() }
    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading=true,error=null)
        val featureResult = schoolRepository.getEntitlements()
        val multiCampusEnabled = when (featureResult) {
            is ApiResult.Success -> featureResult.data.features["multi_campus"] != false
            is ApiResult.Failure -> true
        }
        when(val r=repository.getCampusGates()) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                loading=false,
                campuses=r.data.campuses,
                gates=r.data.gates,
                multiCampusEnabled=multiCampusEnabled
            )
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(loading=false,error=r.message,multiCampusEnabled=multiCampusEnabled)
        }
    }
    fun createCampus(name:String,address:String)=save("Campus created") { repository.createCampus(name.trim(),address.trim()) }
    fun setCampus(id:String,active:Boolean)=save(null) { repository.setCampusActive(id,active) }
    fun createGate(campusId:String,name:String,description:String)=save("Pickup gate created") { repository.createPickupGate(campusId,name.trim(),description.trim()) }
    fun setGate(id:String,active:Boolean)=save(null) { repository.setPickupGateActive(id,active) }
    private fun save(message:String?, block:suspend()->ApiResult<*>){
        if(_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value=_uiState.value.copy(saving=true,error=null,message=null)
            when(val r=block()) {
                is ApiResult.Success -> { _uiState.value=_uiState.value.copy(saving=false,message=message); load() }
                is ApiResult.Failure -> _uiState.value=_uiState.value.copy(saving=false,error=r.message)
            }
        }
    }
}
