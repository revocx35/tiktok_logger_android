package io.github.revocx35.tiktoklogger.ui.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.revocx35.tiktoklogger.core.TtText
import io.github.revocx35.tiktoklogger.core.TextBlock
import io.github.revocx35.tiktoklogger.core.TextNode
import io.github.revocx35.tiktoklogger.ui.theme.Wa
import io.github.revocx35.tiktoklogger.ui.theme.WaColors
import io.github.revocx35.tiktoklogger.ui.theme.WaType

private const val META_ID = "meta"

private fun AnnotatedString.Builder.appendNodes(nodes: List<TextNode>, c: WaColors) {
    for (n in nodes) {
        when (n) {
            is TextNode.Text -> append(n.v)
            // Only http(s) URLs ever become links (TtText.safeHref); they open in the browser on tap.
            is TextNode.Link -> withLink(LinkAnnotation.Url(n.href, TextLinkStyles(SpanStyle(color = c.link)))) { append(n.v) }
            is TextNode.MentionNode -> withStyle(SpanStyle(color = c.link, fontWeight = FontWeight.Medium)) { append("@${n.name}") }
        }
    }
}

/**
 * Renders TikTok message text (links and @usernames from [TtText]) as styled text; message content is never
 * interpreted as markup. [trailing] reserves room at the end of the last line for the bubble's time, the way the
 * timestamp flows into the text.
 */
@Composable
fun WaTextView(
    text: String,
    style: TextStyle = WaType.bubble,
    color: Color = Wa.colors.text,
    trailing: TextUnit = 0.sp,
    modifier: Modifier = Modifier,
) {
    val c = Wa.colors
    val jumbo = remember(text) { TtText.jumboEmoji(text) }
    val lines = remember(text) { TtText.parse(text).filterIsInstance<TextBlock.Line>() }
    val base = if (jumbo) style.copy(fontSize = style.fontSize * 2.4f, lineHeight = style.fontSize * 2.9f) else style
    val inline = if (trailing.value > 0f) {
        mapOf(META_ID to InlineTextContent(Placeholder(trailing, 12.sp, PlaceholderVerticalAlign.TextBottom)) {})
    } else {
        emptyMap()
    }
    Text(
        buildAnnotatedString {
            lines.forEachIndexed { k, line ->
                if (k > 0) append('\n')
                appendNodes(line.c, c)
            }
            if (inline.isNotEmpty()) appendInlineContent(META_ID, " ")
        },
        style = base,
        color = color,
        inlineContent = inline,
        modifier = modifier,
    )
}

/** Whether [WaTextView] ends with inline text, i.e. can host the trailing time (always, for TikTok text). */
@Suppress("UNUSED_PARAMETER")
fun endsWithInlineText(text: String): Boolean = true
