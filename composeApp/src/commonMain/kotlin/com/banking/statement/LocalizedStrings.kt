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
    val total: String,
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
    val duplicateStatement: String,
    val duplicateStatementMessage: String,

    // Account Management
    val manageAccounts: String,
    val noAccounts: String,
    val noAccountsHint: String,
    val deleteAccount: String,
    val deleteAccountConfirm: String,
    val delete: String,
    val dangerZone: String,
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

    // Import Error Dialog
    val importErrorTitle: String,
    val importErrorFileCouldNotBeRead: String,
    val importErrorUnsupportedFormatDetail: String,
    val importErrorNotBankStatementDetail: String,
    val importErrorPdfInvalid: String,
    val importErrorPdfNoText: String,
    val importErrorParserNotFound: String,
    val importErrorGeneric: String,
    val importErrorWhatHappened: String,
    val importErrorWhatYouCanDo: String,
    val importErrorTryDifferentFormat: String,
    val importErrorCheckFileValid: String,
    val importErrorContactSupport: String,
    val importErrorFileLabel: String,
    val importErrorFormatLabel: String,
    val importErrorDetailsLabel: String,
    val retryImport: String,
    val dismiss: String,

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
    val periodCustom: String,
    val selectDateRange: String,
    val startDate: String,
    val endDate: String,
    val apply: String,

    // Category Details
    val topTransactions: String,
    val viewDetails: String,
    val categoryDetails: String,
    val close: String,

    // Category Override
    val changeCategory: String,

    // Bottom Navigation Tabs
    val tabHome: String,
    val tabTransactions: String,
    val tabSpending: String,
    val tabMerchants: String,
    val tabSettings: String,

    // Merchants Screen
    val merchantsTitle: String,
    val topMerchants: String,
    val noMerchantData: String,
    val totalSpent: String,
    val transactionsCount: String,
    val thisMonth: String,
    val lastMonth: String,
    val trend: String,

    // Custom Categories
    val manageCategories: String,
    val customCategories: String,
    val predefinedCategories: String,
    val addCategory: String,
    val newCategory: String,
    val editCategory: String,
    val deleteCategory: String,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val deleteCategoryConfirm: String,
    val noCategoriesYet: String,
    val createFirstCategory: String,

    // Categories (17 consolidated categories)
    val categoryRent: String,
    val categoryTransport: String,
    val categorySupermarket: String,
    val categoryRestaurant: String,
    val categoryShopping: String,
    val categoryHealth: String,
    val categoryInsurance: String,
    val categoryEntertainment: String,
    val categorySubscriptions: String,
    val categoryInvestment: String,
    val categoryTravel: String,
    val categorySalary: String,
    val categoryRefund: String,
    val categoryTransfer: String,
    val categoryEducation: String,
    val categoryTaxes: String,
    val categoryOther: String,

    // Spending Details
    val tapToViewTopTransactions: String,
    val monthlyBreakdown: String,
    val perMonthAverage: String,

    // Bank Selection Dialog
    val selectYourBank: String,
    val multipleBanksDetected: String,
    val select: String,
    val matched: String,
    val confidenceCertain: String,
    val confidenceHigh: String,
    val confidenceMedium: String,

    // Welcome Tutorial & Contact
    val welcomeTitle: String,
    val welcomeBullet1: String,
    val welcomeBullet2: String,
    val welcomeBullet3: String,
    val welcomeButton: String,
    val contactTitle: String,
    val contactEmail: String,
    val contactHint: String,

    // Security
    val security: String,
    val biometricLock: String,
    val biometricLockDescription: String,

    // Onboarding
    val onboardingImportTitle: String,
    val onboardingImportSubtitle: String,
    val onboardingSpendingTitle: String,
    val onboardingSpendingSubtitle: String,
    val onboardingTrendsTitle: String,
    val onboardingTrendsSubtitle: String,
    val onboardingNext: String,
    val onboardingSkip: String,
    val onboardingGetStarted: String,

    // Pagination
    val showMore: String,
    val more: String
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
    homeTitle = "Bank++",
    homeSubtitle = "Import your bank statements to analyze",
    importButton = "Import Statement",
    processing = "Processing...",
    supportedFormats = "PDF • CSV",

    accounts = "Accounts",
    statements = "Statements",
    transactions = "Transactions",
    total = "Total",
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
    duplicateStatement = "This statement was already imported",
    duplicateStatementMessage = "This file has already been imported. No new transactions were added.",

    manageAccounts = "Manage Accounts",
    noAccounts = "No accounts yet",
    noAccountsHint = "Import a bank statement to create your first account",
    deleteAccount = "Delete Account",
    deleteAccountConfirm = "Are you sure you want to delete \"%s\"? This will remove all statements and transactions for this account.",
    delete = "Delete",
    dangerZone = "Danger Zone",
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

    importErrorTitle = "Import Error",
    importErrorFileCouldNotBeRead = "The file could not be read. It may be corrupted or inaccessible.",
    importErrorUnsupportedFormatDetail = "This file format is not supported. Please use PDF, CSV, or Excel files exported from your bank.",
    importErrorNotBankStatementDetail = "The file does not contain recognizable bank statement data. Please ensure you are importing a bank statement file.",
    importErrorPdfInvalid = "This file is not a valid PDF document.",
    importErrorPdfNoText = "No text could be extracted from this PDF. It may be a scanned image. Try using a CSV or Excel export instead.",
    importErrorParserNotFound = "No parser is available for this bank. The file was recognized as a bank statement but could not be processed.",
    importErrorGeneric = "An unexpected error occurred during import.",
    importErrorWhatHappened = "What happened",
    importErrorWhatYouCanDo = "What you can do",
    importErrorTryDifferentFormat = "Try exporting from your bank in a different format (CSV or Excel often works best)",
    importErrorCheckFileValid = "Make sure the file is a valid, unmodified bank statement",
    importErrorContactSupport = "If the problem persists, contact support",
    importErrorFileLabel = "File",
    importErrorFormatLabel = "Format",
    importErrorDetailsLabel = "Details",
    retryImport = "Try Again",
    dismiss = "Dismiss",

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
    periodCustom = "Custom",
    selectDateRange = "Select Date Range",
    startDate = "Start Date",
    endDate = "End Date",
    apply = "Apply",

    topTransactions = "Top Transactions",
    viewDetails = "View Details",
    categoryDetails = "Category Details",
    close = "Close",

    changeCategory = "Change Category",

    // Bottom Navigation Tabs
    tabHome = "Home",
    tabTransactions = "Activity",
    tabSpending = "Spending",
    tabMerchants = "Merchants",
    tabSettings = "Settings",

    // Merchants Screen
    merchantsTitle = "Top Merchants",
    topMerchants = "Top Merchants",
    noMerchantData = "No merchant data yet",
    totalSpent = "Total Spent",
    transactionsCount = "%d transactions",
    thisMonth = "This Month",
    lastMonth = "Last Month",
    trend = "Trend",

    // Custom Categories
    manageCategories = "Manage Categories",
    customCategories = "Custom Categories",
    predefinedCategories = "Predefined Categories",
    addCategory = "Add Category",
    newCategory = "New Category",
    editCategory = "Edit Category",
    deleteCategory = "Delete Category",
    categoryName = "Category Name",
    categoryIcon = "Icon",
    categoryColor = "Color",
    deleteCategoryConfirm = "Are you sure you want to delete this category?",
    noCategoriesYet = "No custom categories yet",
    createFirstCategory = "Create your first custom category",

    // Categories (17 consolidated categories)
    categoryRent = "Rent & Utilities",
    categoryTransport = "Transport",
    categorySupermarket = "Supermarket",
    categoryRestaurant = "Restaurant",
    categoryShopping = "Shopping",
    categoryHealth = "Health",
    categoryInsurance = "Insurance",
    categoryEntertainment = "Entertainment",
    categorySubscriptions = "Subscriptions",
    categoryInvestment = "Investment",
    categoryTravel = "Travel",
    categorySalary = "Salary",
    categoryRefund = "Refund",
    categoryTransfer = "Transfer",
    categoryEducation = "Education",
    categoryTaxes = "Taxes",
    categoryOther = "Other",

    // Spending Details
    tapToViewTopTransactions = "👆 Tap to view top transactions",
    monthlyBreakdown = "Monthly breakdown:",
    perMonthAverage = "/mo avg",

    // Bank Selection Dialog
    selectYourBank = "Select Your Bank",
    multipleBanksDetected = "Multiple banks were detected. Please select the correct one:",
    select = "Select",
    matched = "Matched:",
    confidenceCertain = "Certain",
    confidenceHigh = "High",
    confidenceMedium = "Medium",

    // Welcome Tutorial & Contact
    welcomeTitle = "Welcome to MoneyLupe!",
    welcomeBullet1 = "Import PDF/CSV bank statements",
    welcomeBullet2 = "Track spending by category",
    welcomeBullet3 = "Analyze trends over time",
    welcomeButton = "Got it, let's start",
    contactTitle = "Contact & Feedback",
    contactEmail = "moneylupe.info@gmail.com",
    contactHint = "Questions or feedback? We'd love to hear from you!",

    // Security
    security = "Security",
    biometricLock = "App Lock",
    biometricLockDescription = "Require fingerprint or PIN to open the app",

    // Onboarding
    onboardingImportTitle = "Import your bank statement",
    onboardingImportSubtitle = "Supports 20+ banks. Just drop in a PDF or CSV file and we'll handle the rest.",
    onboardingSpendingTitle = "See where your money goes",
    onboardingSpendingSubtitle = "Automatic categorization shows your spending at a glance. No manual entry needed.",
    onboardingTrendsTitle = "Track trends over time",
    onboardingTrendsSubtitle = "Watch your income and expenses evolve month by month. Spot patterns and save more.",
    onboardingNext = "Next",
    onboardingSkip = "Skip",
    onboardingGetStarted = "Get Started",
    showMore = "Show more",
    more = "more"
)
