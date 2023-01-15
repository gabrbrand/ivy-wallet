package com.ivy.settings

import androidx.lifecycle.viewModelScope
import arrow.core.Either.Left
import arrow.core.Either.Right
import com.ivy.core.domain.SimpleFlowViewModel
import com.ivy.core.domain.action.settings.applocked.AppLockedFlow
import com.ivy.core.domain.action.settings.applocked.WriteAppLockedAct
import com.ivy.core.domain.action.settings.balance.HideBalanceFlow
import com.ivy.core.domain.action.settings.balance.WriteHideBalanceAct
import com.ivy.core.domain.action.settings.basecurrency.BaseCurrencyFlow
import com.ivy.core.domain.action.settings.basecurrency.WriteBaseCurrencyAct
import com.ivy.core.domain.action.settings.startdayofmonth.StartDayOfMonthFlow
import com.ivy.core.domain.action.settings.startdayofmonth.WriteStartDayOfMonthAct
import com.ivy.core.domain.pure.util.combine
import com.ivy.drive.google_drive.GoogleDriveInitializer
import com.ivy.drive.google_drive.GoogleDriveService
import com.ivy.navigation.Navigator
import com.ivy.navigation.destinations.Destination
import com.ivy.old.ImportOldJsonBackupAct
import com.ivy.settings.data.BackupImportState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.io.path.Path

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val navigator: Navigator,
    private val baseCurrencyFlow: BaseCurrencyFlow,
    private val writeBaseCurrencyAct: WriteBaseCurrencyAct,
    private val startDayOfMonthFlow: StartDayOfMonthFlow,
    private val writeStartDayOfMonthAct: WriteStartDayOfMonthAct,
    private val hideBalanceFlow: HideBalanceFlow,
    private val writeHideBalanceAct: WriteHideBalanceAct,
    private val appLockedFlow: AppLockedFlow,
    private val writeAppLockedAct: WriteAppLockedAct,
    private val importOldJsonBackupAct: ImportOldJsonBackupAct,
    private val googleDriveInitializer: GoogleDriveInitializer,
    private val googleDriveService: GoogleDriveService
) : SimpleFlowViewModel<SettingsState, SettingsEvent>() {
    override val initialUi: SettingsState = SettingsState(
        baseCurrency = "",
        startDayOfMonth = 1,
        hideBalance = false,
        appLocked = false,
        driveMounted = false,
        importOldData = BackupImportState.Idle
    )

    private val importOldDataState = MutableStateFlow(initialUi.importOldData)

    override val uiFlow: Flow<SettingsState> = combine(
        baseCurrencyFlow(),
        startDayOfMonthFlow(),
        hideBalanceFlow(Unit),
        appLockedFlow(Unit),
        googleDriveInitializer.driveMounted,
        importOldDataState
    ) { baseCurrency, startDayOfMonth, hideBalance,
        appLocked, driveMounted, importOldData ->
        SettingsState(
            baseCurrency = baseCurrency,
            startDayOfMonth = startDayOfMonth,
            hideBalance = hideBalance,
            appLocked = appLocked,
            driveMounted = driveMounted,
            importOldData = importOldData,
        )
    }

    override suspend fun handleEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.Back -> navigator.back()
            is SettingsEvent.BaseCurrencyChange -> {
                writeBaseCurrencyAct(event.newCurrency)
            }

            is SettingsEvent.StartDayOfMonth -> {
                writeStartDayOfMonthAct(event.startDayOfMonth)
            }

            is SettingsEvent.HideBalance -> {
                writeHideBalanceAct(event.hideBalance)
            }

            is SettingsEvent.AppLocked -> {
                writeAppLockedAct(event.appLocked)
            }

            is SettingsEvent.ImportOldData -> handleImportOldData(event)
            is SettingsEvent.MountDrive -> handleMountDrive()
            SettingsEvent.AddFrame -> handleAddFrame()
        }
    }

    private suspend fun handleImportOldData(event: SettingsEvent.ImportOldData) {
        // Launch new scope so we won't block the event queue
        viewModelScope.launch {
            if (importOldDataState.value != BackupImportState.Idle) return@launch

            importOldDataState.value = BackupImportState.Importing
            val result = importOldJsonBackupAct(event.jsonZipUri)
            importOldDataState.value = when (result) {
                is Left -> {
                    Timber.e("Import error: $result")
                    BackupImportState.Error(message = result.value.toString())
                }

                is Right -> BackupImportState.Success(
                    message = "Faulty transfers: ${result.value.faultyTransfers}"
                )
            }

            delay(10_000) // display for some seconds
            importOldDataState.value = BackupImportState.Idle
        }
    }

    private suspend fun handleMountDrive() {
        if (googleDriveInitializer.driveMounted.value) {
            withContext(Dispatchers.IO) {
                val result = googleDriveService.write(
                    path = Path("Ivy-Wallet-Debug-sync-folder/Backup/test.txt"),
                    content = LocalDateTime.now().toString()
                )
                when (result) {
                    is Left -> Timber.d(
                        "Error writing to drive: ${
                            result.value.exception.also {
                                it.printStackTrace()
                            }.message
                        }"
                    )

                    is Right -> Timber.d("Successfully wrote to drive")
                }
            }
        } else {
            googleDriveInitializer.connect()
        }
    }

    private fun handleAddFrame() {
        navigator.navigate(Destination.addFrame.destination(Unit))
    }
}