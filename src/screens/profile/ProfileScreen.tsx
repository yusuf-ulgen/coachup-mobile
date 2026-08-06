import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { Colors } from '../../theme/colors';
import { useAuth } from '../../context/AuthContext';
import { GYM_CONFIG } from '../../config/gym';

export const ProfileScreen: React.FC = () => {
  const { user, profile, signOut } = useAuth();

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.headerTitle}>Profilim</Text>

      <View style={styles.profileCard}>
        <View style={styles.avatarLarge}>
          <Text style={styles.avatarLargeText}>{profile?.name?.[0] || 'S'}</Text>
        </View>
        <Text style={styles.nameText}>
          {profile?.name || 'Sporcu'} {profile?.surname || ''}
        </Text>
        <Text style={styles.emailText}>{user?.email || 'eposta@coachup.app'}</Text>
        <View style={styles.roleBadge}>
          <Text style={styles.roleBadgeText}>
            {profile?.role === 'admin' ? 'Yönetici' : 'Üye'} - {GYM_CONFIG.GYM_NAME}
          </Text>
        </View>
      </View>

      <View style={styles.menuSection}>
        <TouchableOpacity style={styles.menuItem}>
          <Text style={styles.menuText}>⚙️ Hesap Ayarları</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.menuItem}>
          <Text style={styles.menuText}>💳 Üyelik & Paketler</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.menuItem}>
          <Text style={styles.menuText}>📊 Fiziksel Ölçümler</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.signOutItem} onPress={signOut}>
          <Text style={styles.signOutText}>🚪 Oturumu Kapat</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  content: {
    paddingTop: 60,
    paddingHorizontal: 20,
    paddingBottom: 40,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: '800',
    color: Colors.textDark,
    marginBottom: 20,
  },
  profileCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 24,
    padding: 24,
    alignItems: 'center',
    marginBottom: 24,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  avatarLarge: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  avatarLargeText: {
    color: Colors.allWhite,
    fontWeight: '900',
    fontSize: 32,
  },
  nameText: {
    fontSize: 20,
    fontWeight: '800',
    color: Colors.textDark,
  },
  emailText: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginTop: 4,
    marginBottom: 12,
  },
  roleBadge: {
    backgroundColor: Colors.primaryLight,
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 20,
  },
  roleBadgeText: {
    color: Colors.primary,
    fontWeight: '700',
    fontSize: 12,
  },
  menuSection: {
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  menuItem: {
    paddingVertical: 18,
    paddingHorizontal: 20,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderDark,
  },
  menuText: {
    color: Colors.textDark,
    fontSize: 16,
    fontWeight: '600',
  },
  signOutItem: {
    paddingVertical: 18,
    paddingHorizontal: 20,
  },
  signOutText: {
    color: Colors.error,
    fontSize: 16,
    fontWeight: '700',
  },
});
