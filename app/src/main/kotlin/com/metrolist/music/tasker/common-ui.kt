package com.metrolist.music.tasker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.metrolist.music.R


/**
 * ******************* Input Components **********************
 */

@Composable
fun TextFieldWithTaskerVariables(text: MutableState<String>, options: List<String>) {
    TextField(
        value = text.value,
        onValueChange = { text.value = it },
        label = { Text("Enter text") },
        trailingIcon = {
            if(options.isNotEmpty()) {
                DropdownMenu(text, options)
            }
        }
    )
}

@Composable
fun ChoicesForString(
    radioOptions: List<String>,
    selectedOption: MutableState<String>,
    modifier: Modifier = Modifier
) {

    Column(modifier.selectableGroup()) {
        radioOptions.forEach { text ->
            Row(
                Modifier
                    .height(42.dp)
                    .fillMaxWidth()
                    .selectable(
                        selected = (text == selectedOption.value),
                        onClick = { selectedOption.value = text },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (text == selectedOption.value),
                    onClick = null // null recommended for accessibility with screen readers
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}



@Composable
fun DropdownMenu(targetString: MutableState<String>, options: List<String>) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                ImageVector.vectorResource(id = R.drawable.playlist_add),
                contentDescription = "Add Tasker variable"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        targetString.value += option
                        expanded = false
                    },
                )
            }
        }
    }
}



/**
 * ******************* Configuration Screen **********************
 */

@Composable
fun TaskerConfigurationItem(
    inputLabel: String,
    inputDescription: String,
    inputValue: MutableState<String>,
    inputOptions: List<String> = listOf(),
    taskerVariables: List<String> = listOf(),
    startWitFreeTextEnabled: Boolean = false
) {

    var freeTextEnabled by remember { mutableStateOf(startWitFreeTextEnabled) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = inputLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold )

            Text(text = inputDescription,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = Color.Gray )

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // show the appropriate input field
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    if (inputOptions.isEmpty() || freeTextEnabled) {
                        TextFieldWithTaskerVariables(inputValue, taskerVariables)
                    } else {
                        ChoicesForString(inputOptions, inputValue)
                    }
                }

                // show the free text option
                if (!inputOptions.isEmpty()) {
                    IconButton(
                        onClick = { freeTextEnabled = !freeTextEnabled }
                    ) {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.casino),
                            contentDescription = "Switch freeText mode"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskerConfigurationScreen(title: String = "Tasker Action Configuration", content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.padding(10.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Column(
            modifier = Modifier.padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TaskerConfigurationScreenPreview() {
    TaskerConfigurationScreen {

        TaskerConfigurationItem(
            inputLabel = "Playlist",
            inputDescription = "The name of the playlist to play",
            remember { mutableStateOf("") },
            taskerVariables = listOf("%name", "%asd", "%asd2")
        )

        TaskerConfigurationItem(
            inputLabel = "Like Action",
            inputDescription = "How to set the Like",
            remember { mutableStateOf("") },
            inputOptions = listOf("Like", "Unlike", "Toggle"),
            taskerVariables = listOf("%name", "%asd", "%asd2")
        )

        TaskerConfigurationItem(
            inputLabel = "Command",
            inputDescription = "The playback command to execute",
            remember { mutableStateOf("") },
            inputOptions = listOf("PLAY", "STOP", "ENABLE SHUFFLE", "NEXT SONG"),
            taskerVariables = listOf("%name", "%asd", "%asd2"),
            startWitFreeTextEnabled = true
        )

    }
}

