package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * Interface for bank-specific PDF parsers
 */
interface BankPdfParser {
    /**
     * The name of the bank this parser handles
     */
    val bankName: String

    /**
     * Check if this parser can handle the given PDF text
     */
    fun canParse(pdfText: String): Boolean

    /**
     * Parse the PDF text and extract transactions
     */
    fun parse(pdfText: String, fileName: String): ParseResult
}

/**
 * Registry of all available bank parsers
 */
object BankParserRegistry {
    private val parsers = mutableListOf<BankPdfParser>()

    init {
        // Register all bank parsers
        // IMPORTANT: Order matters! More specific parsers should come BEFORE generic ones

        // International neo-banks first (have very specific identifiers like "revolut")
        register(RevolutPdfParser())  // Revolut MUST be before German banks (they may share generic terms like "girokonto")
        register(N26Parser())
        register(BunqParser())

        // German banks
        // DKB must be before Sparkasse because they share some patterns
        register(DkbParser())  // DKB MUST be before Sparkasse (they share patterns)
        register(SparkasseParser())
        register(IngDiBaParser())  // ING has generic terms like "girokonto" - must be after specific banks
        register(DeutscheBankParser())
        register(PostbankParser())
        register(CommerzbankParser())
        register(VolksbankParser())  // Also handles VR-Bank and Raiffeisenbank
        register(C24Parser())
        register(ConsorsbankParser())
        register(TargobankParser())
        register(DirectBank1822Parser())
        register(TomorrowBankParser())

        // US/UK/Australian banks
        register(BankwestParser())
        register(SunTrustParser())
        register(BankOfAmericaParser())
        register(VirginMoneyParser())
        register(LloydsParser())
        register(WestpacParser())

        // European banks
        register(IngPolandParser())
        register(AbnAmroParser())
        register(FinecoParser())
        register(BancoBpmParser())
        register(IllimityParser())
        register(UniCreditParser())
        register(SocieteGeneraleParser())
        register(NickelParser())

        // Latin American banks
        register(BcpParser())
        register(BancoOccidenteParser())
        register(GrupoInversoresParser())
        register(BbvaFrancesParser())
        register(BancoProvinciaParser())

        // African banks
        register(AfrilandParser())
        register(UbaParser())
        register(JahaParser())
        register(NairobiBankParser())
        register(KcbParser())

        // Asian banks
        register(PosbParser())
        register(HdfcParser())
        register(SbiParser())
        register(CanaraParser())
        register(AlfaBankParser())
        register(MaybankParser())
        register(CimbParser())
        register(NayaPayParser())
        register(McbParser())
        register(AbBankParser())
        register(UcbParser())

        // North American banks (additional)
        register(BankTrnParser())
        register(DaveBankParser())
        register(UsBankParser())
        register(TdBankParser())
        register(WalmartParser())
        register(ScotiaBankParser())
        register(RbcParser())

        // European banks (additional)
        register(MetroBankParser())
        register(WiseParser())
        register(BankOfIrelandParser())
        register(BancaMarchParser())
        register(BancoCttParser())

        // German banks (additional) - from jejik-mt940 PHP library
        register(ApoBankParser())
        register(LandesbankBerlinParser())
        register(HypoVereinsbankParser())
        register(LbbwParser())
        register(ComdirectParser())
        register(SantanderParser())
        register(SpardaBankParser())
        register(PsdBankParser())

        // Generic German bank parser (fallback - should be last for German banks)
        register(GenericGermanBankParser())
    }

    fun register(parser: BankPdfParser) {
        parsers.add(parser)
    }

    /**
     * Find a parser that can handle the given PDF text
     */
    fun findParser(pdfText: String): BankPdfParser? {
        return parsers.find { it.canParse(pdfText) }
    }

    /**
     * Get all registered bank names
     */
    fun supportedBanks(): List<String> = parsers.map { it.bankName }
}
