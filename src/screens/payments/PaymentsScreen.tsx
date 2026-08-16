import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Colors } from '../../theme/colors';
import { CreditCard, CheckCircle, AlertCircle, History, ArrowLeft, Calendar, DollarSign } from 'lucide-react-native';
import { AuthService } from '../../services/authService';
import { supabase } from '../../services/supabaseClient';

export const PaymentsScreen = ({ navigation }: any) => {
  const [loading, setLoading] = useState(true);
  const [memberships, setMemberships] = useState<any[]>([]);
  const [payments, setPayments] = useState<any[]>([]);

  useEffect(() => {
    loadPaymentData();
  }, []);

  const loadPaymentData = async () => {
    setLoading(true);
    try {
      const user = await AuthService.getCurrentUser();
      if (!user) return;

      // 1. Üyelikleri Çek
      const { data: memData } = await supabase
        .from('user_memberships')
        .select(`
          *,
          plan:membership_plans(id, name, price, duration_months, features),
          gym:gyms(id, name)
        `)
        .eq('user_id', user.id)
        .order('created_at', { ascending: false });

      setMemberships(memData || []);

      // 2. Ödeme Kayıtlarını Çek
      const { data: payData } = await supabase
        .from('membership_payments')
        .select('*')
        .eq('user_id', user.id)
        .order('payment_date', { ascending: false });

      setPayments(payData || []);
    } catch (e) {
      console.error('Error loading payments:', e);
    } finally {
      setLoading(false);
    }
  };

  const activeMembership = memberships.find((m) => m.is_active !== false && (!m.end_date || new Date(m.end_date) >= new Date()));

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.headerRow}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.allWhite} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Ödemeler & Üyelik</Text>
      </View>

      {loading ? (
        <View style={styles.centerBox}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : (
        <ScrollView contentContainerStyle={{ padding: 16, gap: 16 }}>
          {/* Aktif Üyelik / Plan Kartı */}
          <View style={styles.card}>
            <View style={styles.cardHeader}>
              <CreditCard color={Colors.primary} size={24} />
              <Text style={styles.cardTitle}>Mevcut Üyelik Durumu</Text>
            </View>
            {activeMembership ? (
              <View style={styles.cardBody}>
                <Text style={styles.label}>
                  Paket:{' '}
                  <Text style={styles.value}>
                    {activeMembership.plan?.name || 'Standart Üyelik'}
                  </Text>
                </Text>
                <Text style={styles.label}>
                  Salon:{' '}
                  <Text style={styles.value}>
                    {activeMembership.gym?.name || 'CoachUP Salonu'}
                  </Text>
                </Text>
                <Text style={styles.label}>
                  Bitiş Tarihi:{' '}
                  <Text style={styles.value}>
                    {activeMembership.end_date
                      ? new Date(activeMembership.end_date).toLocaleDateString('tr-TR')
                      : 'Süresiz'}
                  </Text>
                </Text>
                {activeMembership.plan?.price ? (
                  <Text style={styles.label}>
                    Tutar:{' '}
                    <Text style={styles.value}>{activeMembership.plan.price} TL</Text>
                  </Text>
                ) : null}
              </View>
            ) : (
              <View style={{ paddingVertical: 12 }}>
                <Text style={{ color: Colors.textSecondaryDark, fontSize: 13 }}>
                  Aktif bir salon üyeliğiniz bulunmamaktadır.
                </Text>
              </View>
            )}
          </View>

          {/* Ödeme Geçmişi */}
          <View style={styles.card}>
            <View style={styles.cardHeader}>
              <History color={Colors.primary} size={24} />
              <Text style={styles.cardTitle}>Ödeme Geçmişi</Text>
            </View>

            {payments.length === 0 ? (
              <View style={{ paddingVertical: 16, alignItems: 'center' }}>
                <DollarSign size={32} color={Colors.textSecondaryDark} />
                <Text style={{ color: Colors.textSecondaryDark, marginTop: 8, fontSize: 13 }}>
                  Kayıtlı ödeme geçmişiniz bulunmamaktadır.
                </Text>
              </View>
            ) : (
              payments.map((p) => {
                const isSuccess = p.status === 'completed' || p.status === 'paid' || !p.status;
                return (
                  <View key={p.id} style={styles.historyItem}>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.historyTitle}>
                        {p.notes || 'Üyelik / Hizmet Ödemesi'}
                      </Text>
                      <Text style={styles.historyDate}>
                        {new Date(p.payment_date || p.created_at).toLocaleDateString('tr-TR')} •{' '}
                        {p.payment_method || 'Kredi Kartı / Nakit'}
                      </Text>
                    </View>
                    <View style={{ alignItems: 'flex-end', gap: 4 }}>
                      <Text style={styles.historyAmount}>{p.amount} TL</Text>
                      <View
                        style={[
                          styles.statusBadge,
                          { backgroundColor: isSuccess ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)' },
                        ]}
                      >
                        <Text style={{ color: isSuccess ? '#10B981' : '#EF4444', fontSize: 11, fontWeight: '600' }}>
                          {isSuccess ? 'Ödendi' : p.status}
                        </Text>
                      </View>
                    </View>
                  </View>
                );
              })
            )}
          </View>
        </ScrollView>
      )}
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
  headerTitle: { fontSize: 22, fontWeight: 'bold', color: Colors.allWhite },
  centerBox: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  card: { backgroundColor: Colors.cardDark, padding: 16, borderRadius: 14, borderWidth: 1, borderColor: '#334155' },
  cardHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 12, gap: 8 },
  cardTitle: { fontSize: 17, fontWeight: '700', color: Colors.allWhite },
  cardBody: { gap: 6 },
  label: { fontSize: 13, color: Colors.textSecondaryDark },
  value: { fontWeight: '600', color: Colors.allWhite },
  historyItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#334155',
  },
  historyTitle: { fontSize: 14, fontWeight: '600', color: Colors.allWhite },
  historyDate: { fontSize: 12, color: Colors.textSecondaryDark, marginTop: 2 },
  historyAmount: { fontSize: 15, fontWeight: '700', color: '#10B981' },
  statusBadge: { paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 },
});
