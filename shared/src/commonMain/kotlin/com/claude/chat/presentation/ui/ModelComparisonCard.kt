package com.claude.chat.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.claude.chat.domain.model.ModelComparisonResponse
import com.claude.chat.domain.model.ModelResponse
import kotlin.math.round

/**
 * Format cost with up to 4 decimal places
 */
private fun formatCost(cost: Double): String {
    val rounded = round(cost * 10000) / 10000

    // Format to avoid scientific notation
    return when {
        rounded >= 0.0001 -> {
            // Remove trailing zeros after decimal point
            val formatted = "%.4f".format(rounded)
            formatted.trimEnd('0').trimEnd('.')
        }
        rounded > 0 -> "%.4f".format(rounded).trimEnd('0')
        else -> "0"
    }
}

/**
 * Card displaying comparison results from multiple models
 */
@Composable
fun ModelComparisonCard(
    comparisonResponse: ModelComparisonResponse,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Model Comparison",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Display each model's response
            comparisonResponse.responses.forEachIndexed { index, modelResponse ->
                ModelResponseCard(
                    modelResponse = modelResponse,
                    rank = index + 1
                )

                if (index < comparisonResponse.responses.size - 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ModelResponseCard(
    modelResponse: ModelResponse,
    rank: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Model name and rank
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${rank}. ${modelResponse.modelName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Response content
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    modelResponse.content,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Metrics
            MetricsRow(modelResponse)
        }
    }
}

@Composable
private fun MetricsRow(
    modelResponse: ModelResponse,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MetricChip(
            label = "Time",
            value = "${modelResponse.responseTimeMs}ms",
            modifier = Modifier.weight(1f)
        )
        MetricChip(
            label = "Tokens",
            value = "${modelResponse.inputTokens}/${modelResponse.outputTokens}",
            modifier = Modifier.weight(1f)
        )
        MetricChip(
            label = "Cost",
            value = "\$${formatCost(modelResponse.totalCost)}",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}