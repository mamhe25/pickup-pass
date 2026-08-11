package com.pickuppass.android.ui.schooladmin.campusgates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusGateScreen(viewModel: CampusGateViewModel = hiltViewModel(), onBack:()->Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var addCampus by remember { mutableStateOf(false) }
    var addGate by remember { mutableStateOf(false) }
    Scaffold(topBar={ TopAppBar(title={Text("Campuses & Pickup Gates")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Filled.ArrowBack,"Back")}}) }) { padding ->
        if(state.loading){ Box(Modifier.padding(padding).fillMaxSize()){ FullScreenLoading() }; return@Scaffold }
        LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(Spacing.lg),verticalArrangement=Arrangement.spacedBy(Spacing.md)){
            item { Text("Configure physical campuses and the gates used for student release. Archiving a campus also disables its pickup gates.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
            state.error?.let { item { ErrorBanner(it) } }
            state.message?.let { item { Text(it,color=MaterialTheme.colorScheme.primary) } }
            item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){ Text("Campuses",fontWeight=FontWeight.Bold); FilledTonalButton(onClick={addCampus=true}){Text("Add campus")} } }
            if(state.campuses.isEmpty()) item { InfoCard("No campuses configured","Add your main campus, or leave the system single-site until needed.") }
            items(state.campuses,key={it.id}) { c -> OutlinedCard { Row(Modifier.fillMaxWidth().padding(Spacing.md),verticalAlignment=Alignment.CenterVertically){ Column(Modifier.weight(1f)){Text(c.name,fontWeight=FontWeight.SemiBold); if(c.address.isNotBlank()) Text(c.address,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}; Switch(c.active,{viewModel.setCampus(c.id,it)}) } } }
            item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){ Text("Pickup Gates",fontWeight=FontWeight.Bold); FilledTonalButton(onClick={addGate=true}){Text("Add gate")} } }
            if(state.gates.isEmpty()) item { InfoCard("No pickup gates configured","Add Gate 1, Main Entrance, Carline, or other release points used by your school.") }
            items(state.gates,key={it.id}) { g -> OutlinedCard { Row(Modifier.fillMaxWidth().padding(Spacing.md),verticalAlignment=Alignment.CenterVertically){ Icon(Icons.Filled.LocationOn,null); Spacer(Modifier.width(Spacing.sm)); Column(Modifier.weight(1f)){Text(g.name,fontWeight=FontWeight.SemiBold); val sub=listOf(g.campusName,g.description).filter{it.isNotBlank()}.joinToString(" · "); if(sub.isNotBlank()) Text(sub,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}; Switch(g.active,{viewModel.setGate(g.id,it)}) } } }
        }
    }
    if(addCampus){ var name by remember{mutableStateOf("")}; var address by remember{mutableStateOf("")}; AlertDialog(onDismissRequest={addCampus=false},title={Text("Add campus")},text={Column(verticalArrangement=Arrangement.spacedBy(Spacing.sm)){OutlinedTextField(name,{name=it},label={Text("Campus name")});OutlinedTextField(address,{address=it},label={Text("Address (optional)")})}},confirmButton={Button(enabled=name.isNotBlank()&&!state.saving,onClick={viewModel.createCampus(name,address);addCampus=false}){Text("Create")}},dismissButton={TextButton(onClick={addCampus=false}){Text("Cancel")}}) }
    if(addGate){ var name by remember{mutableStateOf("")}; var desc by remember{mutableStateOf("")}; var campusId by remember{mutableStateOf("")}; var exp by remember{mutableStateOf(false)}; AlertDialog(onDismissRequest={addGate=false},title={Text("Add pickup gate")},text={Column(verticalArrangement=Arrangement.spacedBy(Spacing.sm)){ExposedDropdownMenuBox(expanded=exp,onExpandedChange={exp=!exp}){OutlinedTextField(value=state.campuses.firstOrNull{it.id==campusId}?.name ?: "No campus / school-wide",onValueChange={},readOnly=true,label={Text("Campus")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(exp)},modifier=Modifier.menuAnchor().fillMaxWidth());ExposedDropdownMenu(expanded=exp,onDismissRequest={exp=false}){DropdownMenuItem(text={Text("No campus / school-wide")},onClick={campusId="";exp=false});state.campuses.filter{it.active}.forEach{c->DropdownMenuItem(text={Text(c.name)},onClick={campusId=c.id;exp=false})}}};OutlinedTextField(name,{name=it},label={Text("Gate name")});OutlinedTextField(desc,{desc=it},label={Text("Description (optional)")})}},confirmButton={Button(enabled=name.isNotBlank()&&!state.saving,onClick={viewModel.createGate(campusId,name,desc);addGate=false}){Text("Create")}},dismissButton={TextButton(onClick={addGate=false}){Text("Cancel")}}) }
}

@Composable private fun InfoCard(title:String,body:String){ OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(Spacing.lg)){Text(title,fontWeight=FontWeight.SemiBold);Text(body,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}} }
