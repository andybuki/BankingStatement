package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * Confidence levels for bank detection
 */
enum class DetectionConfidence(val score: Int) {
    NONE(0),           // No match at all
    LOW(25),           // Some generic patterns match
    MEDIUM(50),        // Multiple patterns match but not unique
    HIGH(75),          // Strong indicators found
    CERTAIN(100)       // Unique identifiers found (BIC, specific text)
}

/**
 * Result of bank detection with confidence score
 */
data class BankDetectionResult(
    val parser: BankPdfParser,
    val confidence: DetectionConfidence,
    val matchedIdentifiers: List<String> = emptyList()
)

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
     * @return true if parser can handle this PDF
     */
    fun canParse(pdfText: String): Boolean

    /**
     * Get detection confidence for this PDF
     * @return confidence level and matched identifiers
     */
    fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        // Default implementation - parsers should override for better detection
        return if (canParse(pdfText)) {
            Pair(DetectionConfidence.MEDIUM, emptyList())
        } else {
            Pair(DetectionConfidence.NONE, emptyList())
        }
    }

    /**
     * Parse the PDF text and extract transactions
     */
    fun parse(pdfText: String, fileName: String): ParseResult
}

/**
 * Registry of all available bank parsers with confidence-based detection
 */
object BankParserRegistry {
    private val parsers = mutableListOf<BankPdfParser>()

    init {
        // Register all bank parsers
        // Neo-banks
        register(RevolutPdfParser())
        register(N26Parser())
        register(BunqParser())

        // German banks
        register(DkbParser())
        register(IngDiBaParser())
        register(SparkasseParser())
        register(DeutscheBankParser())
        register(PostbankParser())
        register(CommerzbankParser())
        register(VolksbankParser())
        register(C24Parser())
        register(ConsorsbankParser())
        register(TargobankParser())
        register(DirectBank1822Parser())
        register(TomorrowBankParser())
        register(ApoBankParser())
        register(LandesbankBerlinParser())
        register(HypoVereinsbankParser())
        register(LbbwParser())
        register(ComdirectParser())
        register(SantanderParser())
        register(SpardaBankParser())
        register(PsdBankParser())

        // International banks
        register(BankwestParser())
        register(SunTrustParser())
        register(BankOfAmericaParser())
        register(VirginMoneyParser())
        register(LloydsParser())
        register(WestpacParser())
        register(IngPolandParser())
        register(AbnAmroParser())
        register(FinecoParser())
        register(BancoBpmParser())
        register(IllimityParser())
        register(UniCreditParser())
        register(SocieteGeneraleParser())
        register(NickelParser())
        register(BcpParser())
        register(BancoOccidenteParser())
        register(GrupoInversoresParser())
        register(BbvaFrancesParser())
        register(BancoProvinciaParser())
        register(AfrilandParser())
        register(UbaParser())
        register(JahaParser())
        register(NairobiBankParser())
        register(KcbParser())
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
        register(BankTrnParser())
        register(DaveBankParser())
        register(UsBankParser())
        register(TdBankParser())
        register(WalmartParser())
        register(ScotiaBankParser())
        register(RbcParser())
        register(MetroBankParser())
        register(WiseParser())
        register(BankOfIrelandParser())
        register(BancaMarchParser())
        register(BancoCttParser())

        // Generic fallback (should be last)
        register(GenericGermanBankParser())
    }

    fun register(parser: BankPdfParser) {
        parsers.add(parser)
    }

    /**
     * Find best matching parser based on confidence
     * @return parser if confidence is HIGH or CERTAIN, null if user selection needed
     */
    fun findParser(pdfText: String): BankPdfParser? {
        val results = detectBanks(pdfText)
        if (results.isEmpty()) return null

        val best = results.first()
        // Auto-select only if HIGH or CERTAIN confidence AND significantly better than second choice
        if (best.confidence.score >= DetectionConfidence.HIGH.score) {
            if (results.size == 1 || results[1].confidence.score < DetectionConfidence.MEDIUM.score) {
                return best.parser
            }
        }
        return null
    }

    /**
     * Detect all possible banks with confidence scores
     * Sorted by confidence (highest first)
     */
    fun detectBanks(pdfText: String): List<BankDetectionResult> {
        return parsers
            .map { parser ->
                val (confidence, identifiers) = parser.getConfidence(pdfText)
                BankDetectionResult(parser, confidence, identifiers)
            }
            .filter { it.confidence != DetectionConfidence.NONE }
            .sortedByDescending { it.confidence.score }
    }

    /**
     * Check if user selection is needed (multiple possible banks or low confidence)
     */
    fun needsUserSelection(pdfText: String): Boolean {
        val results = detectBanks(pdfText)
        if (results.isEmpty()) return true

        val best = results.first()
        // Need selection if confidence is not high enough
        if (best.confidence.score < DetectionConfidence.HIGH.score) return true

        // Need selection if multiple parsers have similar confidence
        if (results.size > 1 && results[1].confidence.score >= DetectionConfidence.MEDIUM.score) {
            return true
        }

        return false
    }

    /**
     * Get parser by bank name (for user selection)
     */
    fun getParserByName(bankName: String): BankPdfParser? {
        return parsers.find { it.bankName.equals(bankName, ignoreCase = true) }
    }

    /**
     * Get all registered bank names
     */
    fun supportedBanks(): List<String> = parsers.map { it.bankName }.distinct().sorted()
}
