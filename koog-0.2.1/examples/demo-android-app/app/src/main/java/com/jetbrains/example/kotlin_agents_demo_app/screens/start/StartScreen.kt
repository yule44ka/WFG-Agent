package com.jetbrains.example.kotlin_agents_demo_app.screens.start

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jetbrains.example.kotlin_agents_demo_app.NavRoute
import com.jetbrains.example.kotlin_agents_demo_app.theme.AppDimension
import com.jetbrains.example.kotlin_agents_demo_app.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    viewModel: StartViewModel = viewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToAgentDemo: (NavRoute.AgentDemoRoute) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    StartScreenContent(
        cards = uiState.demoCards,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToAgentDemo = onNavigateToAgentDemo,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartScreenContent(
    cards: List<CardItem>,
    onNavigateToSettings: () -> Unit,
    onNavigateToAgentDemo: (NavRoute.AgentDemoRoute) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(AppDimension.spacingContentPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Settings icon at the top-left corner
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(AppDimension.iconButtonSizeLarge)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Main content with centered cards and headline
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Headline centered between top of cards and top of parent
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Koog Agents Demo",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.padding(AppDimension.spacingBetweenItems))

                    Text(
                        text = "Check these demos that we got for you.\nDon't forget to specify your tokens in the settings first!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                }

                // Cards list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(3f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    cards.forEach { card ->
                        CardItem(
                            title = card.title,
                            description = card.description,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = AppDimension.spacingMedium),
                            onClick = {
                                card.agentDemoRoute?.let { demoRoute -> onNavigateToAgentDemo(demoRoute) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardItem(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = AppDimension.elevationMedium
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(AppDimension.spacingMedium)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = AppDimension.spacingSmall)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun StartScreenContentPreview() {
    AppTheme {
        StartScreenContent(
            cards = listOf(
                CardItem(
                    title = "First agent",
                    description = "First agent description. It does some cool stuff. Probably"
                ),
                CardItem(
                    title = "Second agent",
                    description = "Second agent description. It does some cool stuff. Probably"
                ),
            ),
            onNavigateToSettings = {},
            onNavigateToAgentDemo = {}
        )
    }
}
