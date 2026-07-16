package com.app.coachup.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.Address
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

private val turkishCities = listOf(
    "Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Amasya", "Ankara", "Antalya",
    "Artvin", "Aydın", "Balıkesir", "Bilecik", "Bingöl", "Bitlis", "Bolu",
    "Burdur", "Bursa", "Çanakkale", "Çankırı", "Çorum", "Denizli", "Diyarbakır",
    "Edirne", "Elazığ", "Erzincan", "Erzurum", "Eskişehir", "Gaziantep", "Giresun",
    "Gümüşhane", "Hakkari", "Hatay", "Isparta", "Mersin", "İstanbul", "İzmir",
    "Kars", "Kastamonu", "Kayseri", "Kırklareli", "Kırşehir", "Kocaeli", "Konya",
    "Kütahya", "Malatya", "Manisa", "Kahramanmaraş", "Mardin", "Muğla", "Muş",
    "Nevşehir", "Niğde", "Ordu", "Rize", "Sakarya", "Samsun", "Siirt", "Sinop",
    "Sivas", "Tekirdağ", "Tokat", "Trabzon", "Tunceli", "Şanlıurfa", "Uşak",
    "Van", "Yozgat", "Zonguldak", "Aksaray", "Bayburt", "Karaman", "Kırıkkale",
    "Batman", "Şırnak", "Bartın", "Ardahan", "Iğdır", "Yalova", "Karabük",
    "Kilis", "Osmaniye", "Düzce"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressScreen(
    navController: NavController
) {
    var addressTitle by remember { mutableStateOf("Ev") }
    var city by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var neighborhood by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var buildingNo by remember { mutableStateOf("") }
    var apartmentNo by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cityExpanded by remember { mutableStateOf(false) }
    var addressId by remember { mutableStateOf<String?>(null) }
    var userId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val uid = AuthService.getCurrentUserId() ?: return@LaunchedEffect
        userId = uid
        try {
            val address = UserService.fetchAddresses(uid)
            if (address != null) {
                addressId = address.id
                addressTitle = address.title
                city = address.city
                district = address.district
                neighborhood = address.neighborhood.orEmpty()
                street = address.street.orEmpty()
                buildingNo = address.buildingNo.orEmpty()
                apartmentNo = address.apartmentNo.orEmpty()
                postalCode = address.postalCode.orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Hata") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("Tamam", color = Primary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.sm, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = "Adres",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AddressTextField(
                label = "Adres Başlığı",
                placeholder = "Ev, İş, vb.",
                value = addressTitle,
                onValueChange = { addressTitle = it }
            )

            // City Dropdown
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "İl", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = cityExpanded,
                    onExpandedChange = { cityExpanded = it }
                ) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        placeholder = { Text("Şehir seçin", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = RoundedCornerShape(Radius.item)
                    )
                    ExposedDropdownMenu(
                        expanded = cityExpanded,
                        onDismissRequest = { cityExpanded = false }
                    ) {
                        turkishCities
                            .filter { it.contains(city, ignoreCase = true) }
                            .forEach { cityOption ->
                                DropdownMenuItem(
                                    text = { Text(cityOption) },
                                    onClick = {
                                        city = cityOption
                                        cityExpanded = false
                                    }
                                )
                            }
                    }
                }
            }

            AddressTextField(
                label = "İlçe",
                placeholder = "İlçe giriniz",
                value = district,
                onValueChange = { district = it }
            )

            AddressTextField(
                label = "Mahalle",
                placeholder = "Mahalle giriniz",
                value = neighborhood,
                onValueChange = { neighborhood = it }
            )

            AddressTextField(
                label = "Cadde/Sokak",
                placeholder = "Cadde veya sokak adı",
                value = street,
                onValueChange = { street = it }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    AddressTextField(
                        label = "Bina No",
                        placeholder = "No",
                        value = buildingNo,
                        onValueChange = { buildingNo = it }
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    AddressTextField(
                        label = "Daire No",
                        placeholder = "No",
                        value = apartmentNo,
                        onValueChange = { apartmentNo = it }
                    )
                }
            }

            AddressTextField(
                label = "Posta Kodu",
                placeholder = "Posta kodu",
                value = postalCode,
                onValueChange = { postalCode = it },
                keyboardType = KeyboardType.Number
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Save Button
        Button(
            onClick = {
                if (city.isBlank() || district.isBlank()) {
                    errorMessage = "İl ve ilçe alanları zorunludur"
                    return@Button
                }
                isLoading = true
                val uid = userId
                if (uid == null) {
                    isLoading = false
                    errorMessage = "Oturum bulunamadı"
                    return@Button
                }
                scope.launch {
                    try {
                        val address = Address(
                            id = addressId ?: UUID.randomUUID().toString(),
                            userId = uid,
                            title = addressTitle.ifBlank { "Ev" },
                            city = city,
                            district = district,
                            neighborhood = neighborhood.ifBlank { null },
                            street = street.ifBlank { null },
                            buildingNo = buildingNo.ifBlank { null },
                            apartmentNo = apartmentNo.ifBlank { null },
                            postalCode = postalCode.ifBlank { null },
                            isDefault = true
                        )
                        UserService.saveAddress(address)
                        isLoading = false
                        navController.popBackStack()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = e.message ?: "Adres kaydedilemedi"
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = 40.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(Radius.pill),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = "Adresi Kaydet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun AddressTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                cursorColor = Primary
            ),
            shape = RoundedCornerShape(Radius.item),
            singleLine = true
        )
    }
}
