package com.pickuppass.android.ui.teacher.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.AcademicStructureResponse
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.model.TenantEntitlementsResponse
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.TeacherOperationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

data class TeacherOperationsUiState(
    val gatesLoading: Boolean = true,
    val gates: List<PickupGateItem> = emptyList(),
    val gatesError: String? = null,

    val structureLoading: Boolean = true,
    val structure: AcademicStructureResponse? = null,
    val structureError: String? = null,

    val entitlementsLoading: Boolean = true,
    val entitlements: TenantEntitlementsResponse? = null,
    val entitlementsError: String? = null
)

@HiltViewModel
class TeacherOperationsViewModel @Inject constructor(
    private val repository:
        TeacherOperationsRepository
) : ViewModel() {

    private val _uiState =
        kotlinx.coroutines.flow.MutableStateFlow(
            TeacherOperationsUiState()
        )

    val uiState:
        kotlinx.coroutines.flow.StateFlow<
            TeacherOperationsUiState
        > =
        _uiState

    init {
        refresh()
    }

    fun refresh() {
        loadGates()
        loadStructure()
        loadEntitlements()
    }

    fun loadGates() {
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    gatesLoading = true,
                    gatesError = null
                )

            when (
                val result =
                    repository
                        .getPickupGates()
            ) {
                is ApiResult.Success -> {
                    _uiState.value =
                        _uiState.value.copy(
                            gatesLoading = false,
                            gates =
                                result.data,
                            gatesError = null
                        )
                }

                is ApiResult.Failure -> {
                    _uiState.value =
                        _uiState.value.copy(
                            gatesLoading = false,
                            gatesError =
                                result.message
                        )
                }
            }
        }
    }

    fun loadStructure() {
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    structureLoading = true,
                    structureError = null
                )

            when (
                val result =
                    repository
                        .getAcademicStructure()
            ) {
                is ApiResult.Success -> {
                    _uiState.value =
                        _uiState.value.copy(
                            structureLoading = false,
                            structure =
                                result.data,
                            structureError = null
                        )
                }

                is ApiResult.Failure -> {
                    _uiState.value =
                        _uiState.value.copy(
                            structureLoading = false,
                            structureError =
                                result.message
                        )
                }
            }
        }
    }

    fun loadEntitlements() {
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    entitlementsLoading = true,
                    entitlementsError = null
                )

            when (
                val result =
                    repository
                        .getTenantEntitlements()
            ) {
                is ApiResult.Success -> {
                    _uiState.value =
                        _uiState.value.copy(
                            entitlementsLoading = false,
                            entitlements =
                                result.data,
                            entitlementsError =
                                null
                        )
                }

                is ApiResult.Failure -> {
                    _uiState.value =
                        _uiState.value.copy(
                            entitlementsLoading = false,
                            entitlementsError =
                                result.message
                        )
                }
            }
        }
    }
}
