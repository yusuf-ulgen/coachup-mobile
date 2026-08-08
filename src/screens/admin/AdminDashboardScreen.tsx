import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, ActivityIndicator, Modal, TextInput } from 'react-native';
import { feedback } from '../../services/feedbackService';
import { Colors } from '../../theme/colors';
import { GYM_CONFIG } from '../../config/gym';
import { Users, LogIn, Clock, DollarSign, Check, X, Megaphone, ArrowLeft } from 'lucide-react-native';
import { supabase } from '../../services/supabaseClient';

export const AdminDashboardScreen = ({ navigation }: any) => {
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalMembers: 0,
    todayEntries: 0,
    pendingRequests: 0,
    monthlyRevenue: 0,
  });
  const [pendingMemberships, setPendingMemberships] = useState<any[]>([]);
  const [showAnnouncementModal, setShowAnnouncementModal] = useState(false);
  const [announcementContent, setAnnouncementContent] = useState('');

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      // Mock veriler (gerçek sistemde supabase'den çekilmeli, şimdilik UI göstermek için)
      setStats({
        totalMembers: 145,
        todayEntries: 32,
        pendingRequests: 3,
        monthlyRevenue: 24500,
      });

      setPendingMemberships([
        { id: 1, name: 'Ahmet Yılmaz', plan: 'Aylık Premium', date: '2026-08-07' },
        { id: 2, name: 'Ayşe Kaya', plan: 'Yıllık Standart', date: '2026-08-06' },
        { id: 3, name: 'Mehmet Demir', plan: '6 Aylık Pro', date: '2026-08-05' },
      ]);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = (id: number) => {
    setPendingMemberships(prev => prev.filter(m => m.id !== id));
    feedback.toast('Üyelik onaylandı.', 'success');
  };

  const handleReject = (id: number) => {
    setPendingMemberships(prev => prev.filter(m => m.id !== id));
    feedback.toast('Üyelik reddedildi.', 'info');
  };

  const handlePublishAnnouncement = async () => {
    if (!announcementContent.trim()) {
      feedback.warning({ title: 'Hata', message: 'Duyuru içeriği boş olamaz.' });
      return;
    }
    // Supabase entegrasyonu (community_posts veya benzeri bir tabloya eklenebilir)
    feedback.success({ title: 'Başarılı', message: 'Duyuru yayınlandı.' });
    setShowAnnouncementModal(false);
    setAnnouncementContent('');
  };

  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color={Colors.primary} />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <ArrowLeft size={24} color={Colors.allWhite} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Admin Paneli</Text>
      </View>

      <ScrollView style={styles.content}>
        <View style={styles.statsGrid}>
          <View style={styles.statCard}>
            <Users size={24} color={Colors.primary} />
            <Text style={styles.statValue}>{stats.totalMembers}</Text>
            <Text style={styles.statLabel}>Toplam Üye</Text>
          </View>
          <View style={styles.statCard}>
            <LogIn size={24} color={Colors.success} />
            <Text style={styles.statValue}>{stats.todayEntries}</Text>
            <Text style={styles.statLabel}>Bugünkü Giriş</Text>
          </View>
          <View style={styles.statCard}>
            <Clock size={24} color={Colors.warning} />
            <Text style={styles.statValue}>{stats.pendingRequests}</Text>
            <Text style={styles.statLabel}>Bekleyen Talep</Text>
          </View>
          <View style={styles.statCard}>
            <DollarSign size={24} color={Colors.primary} />
            <Text style={styles.statValue}>₺{stats.monthlyRevenue}</Text>
            <Text style={styles.statLabel}>Aylık Ciro</Text>
          </View>
        </View>

        <TouchableOpacity 
          style={styles.announcementButton} 
          onPress={() => setShowAnnouncementModal(true)}
        >
          <Megaphone size={20} color={Colors.allWhite} />
          <Text style={styles.announcementButtonText}>Yeni Duyuru Yayınla</Text>
        </TouchableOpacity>

        <View style={styles.sectionContainer}>
          <Text style={styles.sectionTitle}>Bekleyen Üyelik Talepleri</Text>
          {pendingMemberships.length === 0 ? (
            <Text style={styles.emptyText}>Bekleyen talep bulunmuyor.</Text>
          ) : (
            pendingMemberships.map(request => (
              <View key={request.id} style={styles.requestCard}>
                <View style={styles.requestInfo}>
                  <Text style={styles.requestName}>{request.name}</Text>
                  <Text style={styles.requestPlan}>{request.plan}</Text>
                  <Text style={styles.requestDate}>{request.date}</Text>
                </View>
                <View style={styles.requestActions}>
                  <TouchableOpacity 
                    style={[styles.actionBtn, styles.approveBtn]} 
                    onPress={() => handleApprove(request.id)}
                  >
                    <Check size={18} color={Colors.allWhite} />
                  </TouchableOpacity>
                  <TouchableOpacity 
                    style={[styles.actionBtn, styles.rejectBtn]} 
                    onPress={() => handleReject(request.id)}
                  >
                    <X size={18} color={Colors.allWhite} />
                  </TouchableOpacity>
                </View>
              </View>
            ))
          )}
        </View>
      </ScrollView>

      {/* Duyuru Modalı */}
      <Modal visible={showAnnouncementModal} transparent animationType="slide">
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Yeni Duyuru Yayınla</Text>
            <TextInput
              style={styles.announcementInput}
              placeholder="Duyuru içeriğini buraya yazın..."
              placeholderTextColor={Colors.textSecondaryDark}
              multiline
              numberOfLines={4}
              value={announcementContent}
              onChangeText={setAnnouncementContent}
            />
            <View style={styles.modalButtons}>
              <TouchableOpacity 
                style={[styles.modalBtn, styles.cancelBtn]} 
                onPress={() => setShowAnnouncementModal(false)}
              >
                <Text style={styles.cancelBtnText}>İptal</Text>
              </TouchableOpacity>
              <TouchableOpacity 
                style={[styles.modalBtn, styles.publishBtn]} 
                onPress={handlePublishAnnouncement}
              >
                <Text style={styles.publishBtnText}>Yayınla</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: Colors.backgroundDark,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 50,
    paddingBottom: 16,
    backgroundColor: Colors.cardDark,
  },
  backButton: {
    marginRight: 16,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  content: {
    flex: 1,
    padding: 16,
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    marginBottom: 24,
  },
  statCard: {
    width: '48%',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    alignItems: 'center',
    marginBottom: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  statValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: Colors.allWhite,
    marginTop: 8,
    marginBottom: 4,
  },
  statLabel: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  announcementButton: {
    backgroundColor: Colors.primary,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 16,
    borderRadius: 12,
    marginBottom: 24,
  },
  announcementButtonText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: '600',
    marginLeft: 8,
  },
  sectionContainer: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.allWhite,
    marginBottom: 16,
  },
  emptyText: {
    color: Colors.textSecondaryDark,
    fontStyle: 'italic',
  },
  requestCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    padding: 16,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  requestInfo: {
    flex: 1,
  },
  requestName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: Colors.allWhite,
    marginBottom: 4,
  },
  requestPlan: {
    fontSize: 14,
    color: Colors.primary,
    marginBottom: 2,
  },
  requestDate: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  requestActions: {
    flexDirection: 'row',
    gap: 8,
  },
  actionBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
  },
  approveBtn: {
    backgroundColor: Colors.success,
  },
  rejectBtn: {
    backgroundColor: Colors.error,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.7)',
    justifyContent: 'center',
    padding: 20,
  },
  modalContent: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 20,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.allWhite,
    marginBottom: 16,
  },
  announcementInput: {
    backgroundColor: Colors.backgroundDark,
    borderRadius: 12,
    padding: 12,
    color: Colors.allWhite,
    height: 100,
    textAlignVertical: 'top',
    marginBottom: 20,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  modalButtons: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 12,
  },
  modalBtn: {
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 8,
  },
  cancelBtn: {
    backgroundColor: Colors.borderDark,
  },
  cancelBtnText: {
    color: Colors.allWhite,
    fontWeight: '600',
  },
  publishBtn: {
    backgroundColor: Colors.primary,
  },
  publishBtnText: {
    color: Colors.allWhite,
    fontWeight: '600',
  },
});
