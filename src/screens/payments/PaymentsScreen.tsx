import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Colors } from '../../theme/colors';
import { CreditCard, CheckCircle, AlertCircle, History, ArrowLeft } from 'lucide-react-native';

export const PaymentsScreen = ({ navigation }: any) => {
  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.headerRow}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.allWhite} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Ödemeler & Taksitler</Text>
      </View>
      <ScrollView contentContainerStyle={{ padding: 16 }}>
      {/* Aktif Taksit Planı kartı */}
      <View style={styles.card}>
        <View style={styles.cardHeader}>
          <CreditCard color={Colors.primary} size={24} />
          <Text style={styles.cardTitle}>Aktif Taksit Planı</Text>
        </View>
        <View style={styles.cardBody}>
          <Text style={styles.label}>Toplam Tutar: <Text style={styles.value}>12.500 TL</Text></Text>
          <Text style={styles.label}>Taksit Adedi: <Text style={styles.value}>3/6 Ödendi</Text></Text>
          <Text style={styles.label}>Sonraki Vade Tarihi: <Text style={styles.value}>15.09.2026</Text></Text>
        </View>
      </View>

      {/* Taksit listesi */}
      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Taksit Listesi</Text>
        <View style={styles.installmentItem}>
          <Text style={styles.installmentText}>1. Taksit - 15.07.2026</Text>
          <View style={styles.badgeSuccess}>
            <CheckCircle color={Colors.white} size={16} />
            <Text style={styles.badgeTextSuccess}>Ödendi</Text>
          </View>
        </View>
        <View style={styles.installmentItem}>
          <Text style={styles.installmentText}>2. Taksit - 15.08.2026</Text>
          <View style={styles.badgeDanger}>
            <AlertCircle color={Colors.white} size={16} />
            <Text style={styles.badgeTextDanger}>Gecikmiş</Text>
          </View>
        </View>
      </View>

      {/* Finansal İşlem Geçmişi */}
      <View style={styles.card}>
        <View style={styles.cardHeader}>
          <History color={Colors.primary} size={24} />
          <Text style={styles.cardTitle}>Finansal İşlem Geçmişi</Text>
        </View>
        <View style={styles.historyItem}>
          <Text style={styles.historyTitle}>Üyelik Ödemesi</Text>
          <Text style={styles.historyAmount}>-2.500 TL</Text>
        </View>
        <View style={styles.historyItem}>
          <Text style={styles.historyTitle}>PT Dersi Ödemesi</Text>
          <Text style={styles.historyAmount}>-1.200 TL</Text>
        </View>
        <View style={styles.historyItem}>
          <Text style={styles.historyTitle}>Ürün İadesi</Text>
          <Text style={[styles.historyAmount, { color: Colors.success }]}>+450 TL</Text>
        </View>
      </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.backgroundDark },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 8,
  },
  backBtn: {
    padding: 8,
    marginRight: 8,
  },
  headerTitle: { fontSize: 24, fontWeight: 'bold', color: Colors.allWhite },
  card: { backgroundColor: Colors.cardDark, padding: 16, borderRadius: 12, marginBottom: 16 },
  cardHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
  cardTitle: { fontSize: 18, fontWeight: '600', color: Colors.allWhite, marginLeft: 8 },
  cardBody: { marginTop: 8 },
  label: { fontSize: 14, color: Colors.textSecondaryDark, marginBottom: 4 },
  value: { fontWeight: 'bold', color: Colors.allWhite },
  sectionTitle: { fontSize: 16, fontWeight: '600', color: Colors.allWhite, marginBottom: 12 },
  installmentItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: Colors.borderDark },
  installmentText: { fontSize: 14, color: Colors.allWhite },
  badgeSuccess: { flexDirection: 'row', alignItems: 'center', backgroundColor: Colors.success, paddingHorizontal: 8, paddingVertical: 4, borderRadius: 16 },
  badgeTextSuccess: { color: Colors.allWhite, fontSize: 12, marginLeft: 4, fontWeight: '500' },
  badgeDanger: { flexDirection: 'row', alignItems: 'center', backgroundColor: Colors.error, paddingHorizontal: 8, paddingVertical: 4, borderRadius: 16 },
  badgeTextDanger: { color: Colors.allWhite, fontSize: 12, marginLeft: 4, fontWeight: '500' },
  historyItem: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: Colors.borderDark },
  historyTitle: { fontSize: 14, color: Colors.allWhite },
  historyAmount: { fontSize: 14, fontWeight: 'bold', color: Colors.allWhite },
});
