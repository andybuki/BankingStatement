package com.banking.statement

import androidx.compose.runtime.Composable

/**
 * Contains all localized strings for the app
 */
data class AppStrings(
    // Home Screen
    val appName: String,
    val homeTitle: String,
    val homeSubtitle: String,
    val importButton: String,
    val processing: String,
    val supportedFormats: String,

    // Stats
    val accounts: String,
    val statements: String,
    val transactions: String,
    val viewTransactions: String,
    val viewSpending: String,

    // Import Results
    val importSuccessful: String,
    val importFailed: String,
    val bankLabel: String,
    val periodLabel: String,
    val accountLabel: String,
    val importTip: String,

    // Transaction List
    val transactionListTitle: String,
    val noTransactions: String,
    val importFirst: String,
    val back: String,

    // Spending Overview
    val spendingTitle: String,
    val totalIncome: String,
    val totalExpenses: String,
    val netBalance: String,
    val spendingByCategory: String,
    val monthlySummary: String,
    val income: String,
    val expenses: String,
    val noSpendingData: String,
    val net: String,

    // Import Dialog
    val newBankStatement: String,
    val whereToGo: String,
    val createNewAccount: String,
    val addToExisting: String,
    val accountName: String,
    val cancel: String,
    val importAction: String,
    val ibanLabel: String,
    val addToAccount: String,

    // Success Dialog
    val accountCreated: String,
    val importComplete: String,
    val done: String,
    val add: String,

    // Account Management
    val manageAccounts: String,
    val noAccounts: String,
    val noAccountsHint: String,
    val deleteAccount: String,
    val deleteAccountConfirm: String,
    val delete: String,
    val clearAllData: String,
    val clearAllDataConfirm: String,
    val clear: String,
    val accountDeleted: String,
    val allDataCleared: String,
    val statementsCount: String,
    val edit: String,
    val save: String,
    val editAccount: String,
    val allAccounts: String,

    // Errors
    val errorReadingFile: String,
    val errorUnsupportedFormat: String,
    val errorNotBankStatement: String,
    val importCancelled: String,

    // Export
    val export: String,
    val exportCsv: String,
    val exportPdf: String,
    val share: String,
    val exportSuccess: String,
    val exportError: String,
    val exportTransactions: String,
    val exportSpending: String,

    // Settings / Theme
    val settings: String,
    val theme: String,
    val themeLight: String,
    val themeDark: String,
    val themeSystem: String,

    // Search
    val searchTransactions: String,
    val filtered: String,

    // Time Periods
    val periodWeek: String,
    val periodMonth: String,
    val periodYear: String,
    val periodAll: String,

    // Category Override
    val changeCategory: String,

    // Categories
    val categoryRent: String,
    val categoryUtilities: String,
    val categoryPublicTransport: String,
    val categoryCar: String,
    val categorySupermarket: String,
    val categoryRestaurant: String,
    val categoryShopping: String,
    val categoryHealth: String,
    val categoryInsurance: String,
    val categoryEntertainment: String,
    val categorySubscriptions: String,
    val categoryPhoneInternet: String,
    val categoryBankFees: String,
    val categoryInvestment: String,
    val categoryFitness: String,
    val categoryTravel: String,
    val categorySalary: String,
    val categoryRefund: String,
    val categoryTransfer: String,
    val categoryCash: String,
    val categoryPaymentService: String,
    val categoryEducation: String,
    val categoryPets: String,
    val categoryGifts: String,
    val categoryIncome: String,
    val categoryOther: String,
    val categoryGroceries: String,
    val categoryTransport: String,
    val categoryOnlineShopping: String,
    val categoryTaxes: String
)

/**
 * Provides localized strings - implemented per platform
 */
@Composable
expect fun provideStrings(): AppStrings

/**
 * Default English strings for fallback
 */
fun defaultEnglishStrings() = AppStrings(
    appName = "Bank++",
    homeTitle = "Bank Statement Analyzer",
    homeSubtitle = "Import your bank statements to analyze",
    importButton = "Import Statement",
    processing = "Processing...",
    supportedFormats = "PDF • CSV",

    accounts = "Accounts",
    statements = "Statements",
    transactions = "Transactions",
    viewTransactions = "Transactions",
    viewSpending = "Spending",

    importSuccessful = "Import Successful",
    importFailed = "Import Failed",
    bankLabel = "Bank",
    periodLabel = "Period",
    accountLabel = "Account",
    importTip = "Tip: Make sure the file contains valid bank statement data with dates and amounts.",

    transactionListTitle = "Transactions",
    noTransactions = "No transactions yet",
    importFirst = "Import a bank statement to see your transactions",
    back = "Back",

    spendingTitle = "Spending Overview",
    totalIncome = "Total Income",
    totalExpenses = "Total Expenses",
    netBalance = "Net Balance",
    spendingByCategory = "Spending by Category",
    monthlySummary = "Monthly Summary",
    income = "Income",
    expenses = "Expenses",
    noSpendingData = "No spending data available",
    net = "Net",

    newBankStatement = "New Bank Statement",
    whereToGo = "Where should these transactions go?",
    createNewAccount = "Create new account",
    addToExisting = "Add to existing account",
    accountName = "Account name",
    cancel = "Cancel",
    importAction = "Import",
    ibanLabel = "IBAN",
    addToAccount = "Add to %s?",

    accountCreated = "Account Created!",
    importComplete = "Import Complete!",
    done = "Done",
    add = "Add",

    manageAccounts = "Manage Accounts",
    noAccounts = "No accounts yet",
    noAccountsHint = "Import a bank statement to create your first account",
    deleteAccount = "Delete Account",
    deleteAccountConfirm = "Are you sure you want to delete \"%s\"? This will remove all statements and transactions for this account.",
    delete = "Delete",
    clearAllData = "Clear All Data",
    clearAllDataConfirm = "Are you sure you want to delete all data? This will remove all accounts, statements, and transactions. This action cannot be undone.",
    clear = "Clear",
    accountDeleted = "Account deleted",
    allDataCleared = "All data cleared",
    statementsCount = "%d statements",
    edit = "Edit",
    save = "Save",
    editAccount = "Edit Account",
    allAccounts = "All Accounts",

    errorReadingFile = "Could not read file",
    errorUnsupportedFormat = "Unsupported file format",
    errorNotBankStatement = "This does not appear to be a bank statement",
    importCancelled = "Import cancelled",

    export = "Export",
    exportCsv = "Export as CSV",
    exportPdf = "Export as PDF",
    share = "Share",
    exportSuccess = "Export successful",
    exportError = "Export failed",
    exportTransactions = "Transactions Export",
    exportSpending = "Spending Overview Export",

    settings = "Settings",
    theme = "Theme",
    themeLight = "Light",
    themeDark = "Dark",
    themeSystem = "System",

    searchTransactions = "Search transactions...",
    filtered = "filtered",

    periodWeek = "Week",
    periodMonth = "Month",
    periodYear = "Year",
    periodAll = "All",

    changeCategory = "Change Category",

    // Categories
    categoryRent = "Rent",
    categoryUtilities = "Utilities",
    categoryPublicTransport = "Public Transport",
    categoryCar = "Car & Fuel",
    categorySupermarket = "Supermarket",
    categoryRestaurant = "Restaurant & Food",
    categoryShopping = "Shopping",
    categoryHealth = "Health",
    categoryInsurance = "Insurance",
    categoryEntertainment = "Entertainment",
    categorySubscriptions = "Subscriptions",
    categoryPhoneInternet = "Phone & Internet",
    categoryBankFees = "Bank Fees",
    categoryInvestment = "Investment",
    categoryFitness = "Fitness & Sports",
    categoryTravel = "Travel",
    categorySalary = "Salary",
    categoryRefund = "Refund",
    categoryTransfer = "Transfer",
    categoryCash = "Cash Withdrawal",
    categoryPaymentService = "Payment Service",
    categoryEducation = "Education",
    categoryPets = "Pets",
    categoryGifts = "Gifts",
    categoryIncome = "Income",
    categoryOther = "Other",
    categoryGroceries = "Groceries",
    categoryTransport = "Transport",
    categoryOnlineShopping = "Online Shopping",
    categoryTaxes = "Taxes"
)
