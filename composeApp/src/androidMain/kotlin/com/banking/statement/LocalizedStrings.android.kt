package com.banking.statement

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
actual fun provideStrings(): AppStrings = AppStrings(
    appName = stringResource(R.string.app_name),
    homeTitle = stringResource(R.string.home_title),
    homeSubtitle = stringResource(R.string.home_subtitle),
    importButton = stringResource(R.string.import_button),
    processing = stringResource(R.string.processing),
    supportedFormats = stringResource(R.string.supported_formats),

    accounts = stringResource(R.string.accounts),
    statements = stringResource(R.string.statements),
    transactions = stringResource(R.string.transactions),
    viewTransactions = stringResource(R.string.view_transactions),
    viewSpending = stringResource(R.string.view_spending),

    importSuccessful = stringResource(R.string.import_successful),
    importFailed = stringResource(R.string.import_failed),
    bankLabel = stringResource(R.string.bank_label),
    periodLabel = stringResource(R.string.period_label),
    accountLabel = stringResource(R.string.account_label),
    importTip = stringResource(R.string.import_tip),

    transactionListTitle = stringResource(R.string.transaction_list_title),
    noTransactions = stringResource(R.string.no_transactions),
    importFirst = stringResource(R.string.import_first),
    back = stringResource(R.string.back),

    spendingTitle = stringResource(R.string.spending_title),
    totalIncome = stringResource(R.string.total_income),
    totalExpenses = stringResource(R.string.total_expenses),
    netBalance = stringResource(R.string.net_balance),
    spendingByCategory = stringResource(R.string.spending_by_category),
    monthlySummary = stringResource(R.string.monthly_summary),
    income = stringResource(R.string.income),
    expenses = stringResource(R.string.expenses),
    noSpendingData = stringResource(R.string.no_spending_data),
    net = stringResource(R.string.net),

    newBankStatement = stringResource(R.string.new_bank_statement),
    whereToGo = stringResource(R.string.where_should_go),
    createNewAccount = stringResource(R.string.create_new_account),
    addToExisting = stringResource(R.string.add_to_existing),
    accountName = stringResource(R.string.account_name),
    cancel = stringResource(R.string.cancel),
    importAction = stringResource(R.string.import_action),
    ibanLabel = stringResource(R.string.iban_label),
    addToAccount = stringResource(R.string.add_to_account),

    accountCreated = stringResource(R.string.account_created),
    importComplete = stringResource(R.string.import_complete),
    done = stringResource(R.string.done),
    add = stringResource(R.string.add),

    manageAccounts = stringResource(R.string.manage_accounts),
    noAccounts = stringResource(R.string.no_accounts),
    noAccountsHint = stringResource(R.string.no_accounts_hint),
    deleteAccount = stringResource(R.string.delete_account),
    deleteAccountConfirm = stringResource(R.string.delete_account_confirm),
    delete = stringResource(R.string.delete),
    clearAllData = stringResource(R.string.clear_all_data),
    clearAllDataConfirm = stringResource(R.string.clear_all_data_confirm),
    clear = stringResource(R.string.clear),
    accountDeleted = stringResource(R.string.account_deleted),
    allDataCleared = stringResource(R.string.all_data_cleared),
    statementsCount = stringResource(R.string.statements_count),
    edit = stringResource(R.string.edit),
    save = stringResource(R.string.save),
    editAccount = stringResource(R.string.edit_account),
    allAccounts = stringResource(R.string.all_accounts),

    errorReadingFile = stringResource(R.string.error_reading_file),
    errorUnsupportedFormat = stringResource(R.string.error_unsupported_format),
    errorNotBankStatement = stringResource(R.string.error_not_bank_statement),
    importCancelled = stringResource(R.string.import_cancelled),

    export = stringResource(R.string.export),
    exportCsv = stringResource(R.string.export_csv),
    exportPdf = stringResource(R.string.export_pdf),
    share = stringResource(R.string.share),
    exportSuccess = stringResource(R.string.export_success),
    exportError = stringResource(R.string.export_error),
    exportTransactions = stringResource(R.string.export_transactions),
    exportSpending = stringResource(R.string.export_spending),

    settings = stringResource(R.string.settings),
    theme = stringResource(R.string.theme),
    themeLight = stringResource(R.string.theme_light),
    themeDark = stringResource(R.string.theme_dark),
    themeSystem = stringResource(R.string.theme_system),

    searchTransactions = stringResource(R.string.search_transactions),
    filtered = stringResource(R.string.filtered)
)
