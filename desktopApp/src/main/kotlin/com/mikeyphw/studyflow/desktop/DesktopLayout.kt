package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DesktopSidebar(
    selectedRoute: DesktopRoute,
    onRouteSelected: (DesktopRoute) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(232.dp)
            .background(MaterialTheme.colors.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = StudyFlowDesktopBoundary.appName,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = desktopText("Desktop preview"),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f),
        )
        Spacer(modifier = Modifier.height(12.dp))
        DesktopRoute.entries.forEach { route ->
            val selected = route == selectedRoute
            if (selected) {
                Button(
                    onClick = { onRouteSelected(route) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(desktopText(route.title))
                }
            } else {
                OutlinedButton(
                    onClick = { onRouteSelected(route) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(desktopText(route.title))
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        DesktopStateMessage(
            title = desktopText("Boundary sealed"),
            message = desktopText("Desktop imports only JVM-safe StudyFlow modules."),
        )
    }
}

@Composable
fun DesktopTopBar(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Divider()
    }
}

@Composable
fun DesktopMetricCard(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = label, color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f))
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text = detail, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DesktopSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScopeCompat.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            ColumnScopeCompat(this).content()
        }
    }
}

@Composable
fun DesktopStateMessage(title: String, message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold)
            Text(text = message, color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f))
        }
    }
}

@Composable
fun DesktopTagRow(tags: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            Text(
                text = tag,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = MaterialTheme.colors.onSurface,
            )
        }
    }
}

class ColumnScopeCompat internal constructor(
    private val columnScope: androidx.compose.foundation.layout.ColumnScope,
) : androidx.compose.foundation.layout.ColumnScope by columnScope

@Composable
fun DesktopInlineStatus(status: DesktopScreenStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = desktopText("Status: "), fontWeight = FontWeight.Bold)
        Text(status.name.lowercase().replaceFirstChar(Char::uppercase))
    }
}
