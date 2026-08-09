import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
} from 'react-native';
import { feedback } from '../../services/feedbackService';
import { ArrowLeft, MapPin } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { UserService } from '../../services/userService';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

interface AddressScreenProps {
  navigation: any;
}

const TURKEY_CITIES = [
  'Adana', 'Adıyaman', 'Afyonkarahisar', 'Ağrı', 'Amasya', 'Ankara', 'Antalya', 'Artvin',
  'Aydın', 'Balıkesir', 'Bilecik', 'Bingöl', 'Bitlis', 'Bolu', 'Burdur', 'Bursa',
  'Çanakkale', 'Çankırı', 'Çorum', 'Denizli', 'Diyarbakır', 'Edirne', 'Elazığ', 'Erzincan',
  'Erzurum', 'Eskişehir', 'Gaziantep', 'Giresun', 'Gümüşhane', 'Hakkari', 'Hatay', 'Isparta',
  'Mersin', 'İstanbul', 'İzmir', 'Kars', 'Kastamonu', 'Kayseri', 'Kırklareli', 'Kırşehir',
  'Kocaeli', 'Konya', 'Kütahya', 'Malatya', 'Manisa', 'Kahramanmaraş', 'Mardin', 'Muğla',
  'Muş', 'Nevşehir', 'Niğde', 'Ordu', 'Rize', 'Sakarya', 'Samsun', 'Siirt', 'Sinop',
  'Sivas', 'Tekirdağ', 'Tokat', 'Trabzon', 'Tunceli', 'Şanlıurfa', 'Uşak', 'Van',
  'Yozgat', 'Zonguldak', 'Aksaray', 'Bayburt', 'Karaman', 'Kırıkkale', 'Batman', 'Şırnak',
  'Bartın', 'Ardahan', 'Iğdır', 'Yalova', 'Karabük', 'Kilis', 'Osmaniye', 'Düzce'
];

export const AddressSettingsScreen: React.FC<AddressScreenProps> = ({ navigation }) => {
  const insets = useSafeAreaInsets();
  const [title, setTitle] = useState('Ev Adresi');
  const [city, setCity] = useState('İstanbul');
  const [district, setDistrict] = useState('');
  const [neighborhood, setNeighborhood] = useState('');
  const [street, setStreet] = useState('');
  const [buildingNo, setBuildingNo] = useState('');
  const [doorNo, setDoorNo] = useState('');
  const [postalCode, setPostalCode] = useState('');

  const [showCityPicker, setShowCityPicker] = useState(false);
  const [loading, setLoading] = useState(false);
  const [userId, setUserId] = useState<string | null>(null);

  useEffect(() => {
    const loadAddress = async () => {
      try {
        const profile = await AuthService.getCurrentProfile();
        if (profile) {
          setUserId(profile.id);
          if (profile.address_title) setTitle(profile.address_title);
          if (profile.city) setCity(profile.city);
          if (profile.district) setDistrict(profile.district);
          if (profile.neighborhood) setNeighborhood(profile.neighborhood);
          if (profile.street) setStreet(profile.street);
          if (profile.building_no) setBuildingNo(profile.building_no);
          if (profile.door_no) setDoorNo(profile.door_no);
          if (profile.postal_code) setPostalCode(profile.postal_code);
        }
      } catch (e) {
        console.error('Error loading address:', e);
      }
    };
    loadAddress();
  }, []);

  const handleSaveAddress = async () => {
    if (!city || !district || !street) {
      feedback.warning({
        title: 'Hata',
        message: 'Lütfen il, ilçe ve cadde/sokak alanlarını doldurun',
      });
      return;
    }

    try {
      setLoading(true);
      if (userId) {
        await UserService.updateUserProfile(userId, {
          address_title: title,
          city,
          district,
          neighborhood,
          street,
          building_no: buildingNo,
          door_no: doorNo,
          postal_code: postalCode,
        });
      }
      feedback.success({
        title: 'Başarılı',
        message: 'Adres bilgileriniz kaydedildi.',
      });
      navigation.goBack();
    } catch (e: any) {
      feedback.error({
        title: 'Hata',
        message: e,
        fallbackMessage: 'Adres kaydedilemedi',
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => navigation.goBack()}
        >
          <ArrowLeft size={20} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Adres Bilgilerim</Text>
        <View style={{ width: 40 }} />
      </View>

      <ScrollView contentContainerStyle={[styles.scrollContent, { paddingBottom: 24 + insets.bottom }]}>
        {/* Title */}
        <View style={styles.inputWrapper}>
          <Text style={styles.label}>Adres Başlığı</Text>
          <TextInput
            style={styles.input}
            placeholder="Örn: Ev, İşyeri"
            placeholderTextColor={Colors.textSecondaryDark}
            value={title}
            onChangeText={setTitle}
          />
        </View>

        {/* City & District Row */}
        <View style={styles.row}>
          <View style={[styles.inputWrapper, { flex: 1 }]}>
            <Text style={styles.label}>İl (Şehir)</Text>
            <TouchableOpacity
              style={styles.input}
              onPress={() => setShowCityPicker(!showCityPicker)}
            >
              <Text style={{ color: Colors.textDark, fontSize: 15 }}>{city}</Text>
            </TouchableOpacity>
          </View>

          <View style={[styles.inputWrapper, { flex: 1 }]}>
            <Text style={styles.label}>İlçe</Text>
            <TextInput
              style={styles.input}
              placeholder="İlçe adı"
              placeholderTextColor={Colors.textSecondaryDark}
              value={district}
              onChangeText={setDistrict}
            />
          </View>
        </View>

        {/* City Picker Dropdown */}
        {showCityPicker && (
          <View style={styles.cityDropdown}>
            <ScrollView style={{ maxHeight: 180 }} nestedScrollEnabled>
              {TURKEY_CITIES.map((c) => (
                <TouchableOpacity
                  key={c}
                  style={styles.cityOption}
                  onPress={() => {
                    setCity(c);
                    setShowCityPicker(false);
                  }}
                >
                  <Text style={{ color: Colors.textDark, fontSize: 14 }}>{c}</Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
          </View>
        )}

        {/* Neighborhood */}
        <View style={styles.inputWrapper}>
          <Text style={styles.label}>Mahalle</Text>
          <TextInput
            style={styles.input}
            placeholder="Mahalle adı"
            placeholderTextColor={Colors.textSecondaryDark}
            value={neighborhood}
            onChangeText={setNeighborhood}
          />
        </View>

        {/* Street */}
        <View style={styles.inputWrapper}>
          <Text style={styles.label}>Cadde / Sokak</Text>
          <TextInput
            style={styles.input}
            placeholder="Cadde, sokak veya site adı"
            placeholderTextColor={Colors.textSecondaryDark}
            value={street}
            onChangeText={setStreet}
          />
        </View>

        {/* Building & Door & Postal Row */}
        <View style={styles.row}>
          <View style={[styles.inputWrapper, { flex: 1 }]}>
            <Text style={styles.label}>Bina No</Text>
            <TextInput
              style={styles.input}
              placeholder="Bina No"
              placeholderTextColor={Colors.textSecondaryDark}
              value={buildingNo}
              onChangeText={setBuildingNo}
            />
          </View>

          <View style={[styles.inputWrapper, { flex: 1 }]}>
            <Text style={styles.label}>Daire No</Text>
            <TextInput
              style={styles.input}
              placeholder="Daire"
              placeholderTextColor={Colors.textSecondaryDark}
              value={doorNo}
              onChangeText={setDoorNo}
            />
          </View>

          <View style={[styles.inputWrapper, { flex: 1 }]}>
            <Text style={styles.label}>Posta Kodu</Text>
            <TextInput
              style={styles.input}
              placeholder="34000"
              placeholderTextColor={Colors.textSecondaryDark}
              value={postalCode}
              onChangeText={setPostalCode}
              keyboardType="number-pad"
            />
          </View>
        </View>

        {/* Save Button */}
        <TouchableOpacity
          style={[styles.saveButton, loading && styles.disabledButton]}
          onPress={handleSaveAddress}
          disabled={loading}
        >
          {loading ? (
            <ActivityIndicator color={Colors.allWhite} />
          ) : (
            <Text style={styles.saveButtonText}>Adresi Kaydet</Text>
          )}
        </TouchableOpacity>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 16,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderDark,
  },
  backButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  scrollContent: {
    padding: 20,
  },
  inputWrapper: {
    marginBottom: 16,
  },
  row: {
    flexDirection: 'row',
    gap: 12,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.textDark,
    marginBottom: 8,
  },
  input: {
    height: 52,
    backgroundColor: Colors.cardDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    borderRadius: 12,
    paddingHorizontal: 16,
    fontSize: 15,
    color: Colors.textDark,
    justifyContent: 'center',
  },
  cityDropdown: {
    backgroundColor: Colors.cardDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    borderRadius: 12,
    marginBottom: 16,
    padding: 8,
  },
  cityOption: {
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderDark,
  },
  saveButton: {
    height: 54,
    backgroundColor: Colors.primary,
    borderRadius: 27,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 16,
  },
  disabledButton: {
    opacity: 0.5,
  },
  saveButtonText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: '700',
  },
});
