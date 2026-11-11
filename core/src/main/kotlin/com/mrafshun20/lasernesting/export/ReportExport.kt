package com.mrafshun20.lasernesting.export

import com.mrafshun20.lasernesting.nesting.NestResult
import com.mrafshun20.lasernesting.nesting.UnplacedReason
import java.util.Locale

/** What the report header needs to know about the job. */
data class ReportInfo(
    val title: String,
    val dateText: String,
    val machineName: String,
    val materialName: String,
    val thicknessMm: Double,
    val sheetWidthMm: Double,
    val sheetLengthMm: Double,
    val kerfMm: Double,
    val gapMm: Double,
    val marginMm: Double,
    val pricePerKg: Double = 0.0
)

/**
 * One self-contained HTML job report (cutting plan): summary, per-sheet
 * figures with the layout drawn, per-part quantities and unplaced parts.
 * Opens in any browser and prints cleanly (Ctrl+P → PDF). Persian or English.
 */
object ReportExport {

    private val en = mapOf(
        "summary" to "Summary", "machine" to "Machine", "material" to "Material",
        "sheet" to "Sheet", "sheets_used" to "Sheets used", "parts" to "Parts placed",
        "utilization" to "Material utilization", "cut_length" to "Cut length",
        "pierces" to "Pierces", "rapid" to "Rapid travel", "time" to "Estimated cutting time",
        "speed" to "Cutting speed", "weight_parts" to "Weight of parts", "weight_sheets" to "Weight of sheets",
        "weight_scrap" to "Scrap weight", "cost" to "Material cost", "spacing" to "Kerf / gap / margin",
        "per_sheet" to "Sheets", "sheet_no" to "Sheet", "util" to "Utilization", "remnant" to "Remnant",
        "part_list" to "Part list", "part" to "Part", "qty_placed" to "Placed", "unplaced" to "Not placed",
        "reason_invalid" to "Invalid size or quantity", "reason_no_area" to "No usable area",
        "reason_no_space" to "No free space on the available sheets", "reason_no_fit" to "Does not fit the sheet",
        "min" to "min", "kg" to "kg", "mm" to "mm", "m" to "m"
    )
    private val fa = mapOf(
        "summary" to "خلاصه", "machine" to "دستگاه", "material" to "جنس",
        "sheet" to "ورق", "sheets_used" to "تعداد ورق مصرفی", "parts" to "قطعات چیده‌شده",
        "utilization" to "بهره‌وری مواد", "cut_length" to "طول برش",
        "pierces" to "تعداد سوراخ‌کاری (پیرس)", "rapid" to "حرکت سریع", "time" to "زمان تقریبی برش",
        "speed" to "سرعت برش", "weight_parts" to "وزن قطعات", "weight_sheets" to "وزن ورق‌ها",
        "weight_scrap" to "وزن ضایعات", "cost" to "هزینه مواد", "spacing" to "کرف / فاصله / حاشیه",
        "per_sheet" to "ورق‌ها", "sheet_no" to "ورق", "util" to "بهره‌وری", "remnant" to "باقیمانده",
        "part_list" to "فهرست قطعات", "part" to "قطعه", "qty_placed" to "چیده‌شده", "unplaced" to "چیده‌نشده",
        "reason_invalid" to "اندازه یا تعداد نامعتبر", "reason_no_area" to "ناحیه قابل‌استفاده ندارد",
        "reason_no_space" to "در ورق‌های موجود جا نیست", "reason_no_fit" to "در ورق جا نمی‌شود",
        "min" to "دقیقه", "kg" to "کیلوگرم", "mm" to "میلی‌متر", "m" to "متر"
    )

    fun html(result: NestResult, info: ReportInfo, persian: Boolean): String {
        val t = if (persian) fa else en
        fun f(v: Double, d: Int = 1) = String.format(Locale.ROOT, "%.${d}f", v)
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val dir = if (persian) "rtl" else "ltr"
        val minutes = result.estimatedCutMinutes
        val timeText = if (minutes >= 60) "${(minutes / 60).toInt()} h ${(minutes % 60).toInt()} ${t["min"]}"
        else "${f(minutes, 1)} ${t["min"]}"

        val sb = StringBuilder()
        sb.append("""<!DOCTYPE html><html lang="${if (persian) "fa" else "en"}" dir="$dir"><head><meta charset="utf-8">""")
        sb.append("<title>${esc(info.title)}</title><style>")
        sb.append(
            """
            body{font-family:Vazirmatn,Tahoma,'Segoe UI',Arial,sans-serif;margin:0;background:#eef1f5;color:#17202a}
            .page{max-width:1000px;margin:24px auto;padding:0 16px}
            header{background:linear-gradient(135deg,#0f2740,#1b4a73);color:#fff;border-radius:18px;padding:22px 26px;margin-bottom:18px}
            header h1{margin:0 0 4px;font-size:24px} header p{margin:0;opacity:.8}
            .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:12px;margin-bottom:18px}
            .card{background:#fff;border-radius:16px;padding:14px 16px;box-shadow:0 1px 4px rgba(15,39,64,.08)}
            .card b{display:block;font-size:22px;margin-top:4px;color:#0f2740} .card span{font-size:13px;color:#5b6b7b}
            h2{font-size:18px;margin:22px 0 10px;color:#0f2740}
            table{width:100%;border-collapse:collapse;background:#fff;border-radius:14px;overflow:hidden;box-shadow:0 1px 4px rgba(15,39,64,.08)}
            th,td{padding:9px 12px;text-align:start;border-bottom:1px solid #e6ebf1;font-size:14px} th{background:#f4f7fb;color:#41566b}
            .sheet{background:#fff;border-radius:16px;padding:14px;margin-bottom:16px;box-shadow:0 1px 4px rgba(15,39,64,.08)}
            .sheet svg{width:100%;height:auto;border-radius:10px;background:#f5f5f5}
            .bar{height:8px;border-radius:6px;background:#e3e9f0;overflow:hidden;margin:6px 0 10px}.bar i{display:block;height:100%;background:#1f9d6b}
            .warn{background:#fff4e5;border-radius:12px;padding:10px 14px;margin:6px 0;color:#8a4b00}
            @media print{body{background:#fff}.card,.sheet,table{box-shadow:none;border:1px solid #d9e1ea}}
            """.trimIndent()
        )
        sb.append("</style></head><body><div class=\"page\">")
        sb.append("<header><h1>${esc(info.title)}</h1><p>${esc(info.dateText)} — ${esc(info.machineName)}</p></header>")

        sb.append("<h2>${t["summary"]}</h2><div class=\"grid\">")
        fun card(label: String, value: String) = sb.append("<div class=\"card\"><span>$label</span><b>$value</b></div>")
        card(t["sheets_used"]!!, "${result.sheetsUsed}")
        card(t["parts"]!!, "${result.totalPartsPlaced} / ${result.totalPartsRequested}")
        card(t["utilization"]!!, "${f(result.overallUtilizationPercent)}%")
        card(t["cut_length"]!!, "${f(result.totalCutLengthMm / 1000.0, 2)} ${t["m"]}")
        card(t["pierces"]!!, "${result.totalPierces}")
        card(t["rapid"]!!, "${f(result.rapidLengthMm / 1000.0, 2)} ${t["m"]}")
        card(t["time"]!!, timeText)
        card(t["speed"]!!, "${f(result.cuttingSpeedUsedMmPerMin / 1000.0, 2)} ${t["m"]}/${t["min"]}")
        card(t["weight_parts"]!!, "${f(result.partWeightKg)} ${t["kg"]}")
        card(t["weight_sheets"]!!, "${f(result.sheetWeightKg)} ${t["kg"]}")
        card(t["weight_scrap"]!!, "${f(result.scrapWeightKg)} ${t["kg"]}")
        if (result.materialCost > 0) card(t["cost"]!!, f(result.materialCost, 0))
        sb.append("</div>")
        sb.append(
            "<div class=\"card\"><span>${t["material"]} / ${t["sheet"]}</span><b style=\"font-size:16px\">" +
                "${esc(info.materialName)} ${f(info.thicknessMm)} ${t["mm"]} — " +
                "${f(info.sheetWidthMm, 0)} × ${f(info.sheetLengthMm, 0)} ${t["mm"]}<br>" +
                "${t["spacing"]}: ${f(info.kerfMm, 2)} / ${f(info.gapMm, 1)} / ${f(info.marginMm, 0)} ${t["mm"]}</b></div>"
        )

        sb.append("<h2>${t["per_sheet"]}</h2>")
        for ((pos, sheet) in result.sheets.withIndex()) {
            sb.append("<div class=\"sheet\"><b>${t["sheet_no"]} ${sheet.index + 1}</b> — ${t["util"]} ${f(sheet.utilizationPercent)}%")
            sb.append("<div class=\"bar\"><i style=\"width:${f(sheet.utilizationPercent.coerceIn(0.0, 100.0))}%\"></i></div>")
            sb.append("${sheet.placed.size} ${t["parts"]} · ${f(sheet.cutLengthMm / 1000.0, 2)} ${t["m"]} · ${sheet.pierces} ${t["pierces"]} · ${f(sheet.cutMinutes, 1)} ${t["min"]}")
            sheet.remnant?.let {
                sb.append(" · ${t["remnant"]} ${f(it.widthMm, 0)} × ${f(it.heightMm, 0)} ${t["mm"]}")
            }
            sb.append("<br><br>")
            sb.append(ExportCore.sheetSvg(result, pos, info.sheetWidthMm, info.sheetLengthMm))
            sb.append("</div>")
        }

        sb.append("<h2>${t["part_list"]}</h2><table><tr><th>${t["part"]}</th><th>${t["qty_placed"]}</th></tr>")
        val counts = LinkedHashMap<String, Int>()
        for (sheet in result.sheets) for (p in sheet.placed) counts[p.name] = (counts[p.name] ?: 0) + 1
        for ((name, n) in counts) sb.append("<tr><td>${esc(name)}</td><td>$n</td></tr>")
        sb.append("</table>")

        if (result.unplaced.isNotEmpty()) {
            sb.append("<h2>${t["unplaced"]}</h2>")
            for (u in result.unplaced) {
                val reason = when (u.reason) {
                    UnplacedReason.INVALID -> t["reason_invalid"]
                    UnplacedReason.NO_USABLE_AREA -> t["reason_no_area"]
                    UnplacedReason.NO_FREE_SPACE -> t["reason_no_space"]
                    UnplacedReason.DOES_NOT_FIT -> t["reason_no_fit"]
                }
                sb.append("<div class=\"warn\">${esc(u.name)} × ${u.quantity} — $reason</div>")
            }
        }
        sb.append("</div></body></html>")
        return sb.toString()
    }
}
