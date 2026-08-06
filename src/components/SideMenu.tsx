import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Modal,
  ScrollView,
  Alert,
  Dimensions,
} from 'react-native';
import {
  X,
  User,
  BarChart2,
  Calendar,
  Users,
  BookOpen,
  Target,
  CreditCard,
  DollarSign,
  FileText,
  MapPin,
  Dumbbell,
  Settings,
  Shield,
  LogOut,
  ChevronRight,
} from 'lucide-react-native';
import { Colors } from '../theme/colors';
import { AuthService } from '../services/authService';

const { width: SCREEN_WIDTH } = Dimensions.get('window');
const DRAWER_WIDTH = SCREEN_WIDTH * 0.75;

interface SideMenuProps {
  visible: boolean;
  onClose: () => void;
  userProfile?: any;
  onNavigate?: (route: string) => void;
  navigation?: any;
}

interface MenuItemData {
  title: string;
  icon: any;
  route: string;
}

const menuItems: MenuItemData[] = [
  { title: 'Aktivite Geçmişi', icon: BarChart2, route: 'PersonalRecords' },
  { title: 'Randevular', icon: Calendar, route: 'Appointments' },
  { title: 'Takvim', icon: Calendar, route: 'Calendar' },
  { title: 'Grup Dersleri', icon: Users, route: 'GroupClasses' },
  { title: 'Beslenme', icon: BookOpen, route: 'Nutrition' },
  { title: 'İlerleme', icon: BarChart2, route: 'Progress' },
  { title: 'Hedefler', icon: Target, route: 'Goals' },
  { title: 'Üyelik', icon: CreditCard, route: 'Membership' },
  { title: 'Ödemeler', icon: DollarSign, route: 'Payments' },
  { title: 'Anketler', icon: FileText, route: 'Surveys' },
  { title: 'Rezervasyon', icon: MapPin, route: 'Reservations' },
  { title: 'Koçlar', icon: Dumbbell, route: 'Coaches' },
  { title: 'Ayarlar', icon: Settings, route: 'Settings' },
];

export const SideMenu: React.FC<SideMenuProps> = ({
  visible,
  onClose,
  userProfile,
  onNavigate,
}) => {
  const handleLogout = () => {
    Alert.alert('Çıkış Yap', 'Hesabınızdan çıkış yapmak istediğinize emin misiniz?', [
      { text: 'İptal', style: 'cancel' },
      {
        text: 'Çıkış Yap',
        style: 'destructive',
        onPress: async () => {
          onClose();
          try {
            await AuthService.signOut();
          } catch (e) {
            console.error('Logout error:', e);
          }
        },
      },
    ]);
  };

  const displayName = userProfile
    ? `${userProfile.name || ''} ${userProfile.surname || ''}`.trim() || 'Kullanıcı'
    : 'Kullanıcı';

  const gymSubTitle = userProfile?.is_individual
    ? 'Bireysel'
    : userProfile?.gym_name || 'Bağlı Salon Yok';

  const isAdmin = userProfile?.role === 'admin' || userProfile?.role === 'gym_manager';

  return (
    <Modal
      visible={visible}
      animationType="fade"
      transparent={true}
      onRequestClose={onClose}
    >
      <View style={styles.overlay}>
        {/* Backdrop clickable area */}
        <TouchableOpacity
          style={styles.backdrop}
          activeOpacity={1}
          onPress={onClose}
        />

        {/* Drawer Sheet Content */}
        <View style={styles.drawerSheet}>
          {/* Close Button */}
          <TouchableOpacity
            style={styles.closeButton}
            onPress={onClose}
            activeOpacity={0.8}
          >
            <X size={18} color={Colors.textDark} />
          </TouchableOpacity>

          {/* User Info Card */}
          <TouchableOpacity
            style={styles.userCard}
            onPress={() => {
              onClose();
              if (onNavigate) onNavigate('Profile');
              else if (navigation) navigation.navigate('Profile');
            }}
            activeOpacity={0.8}
          >
            <User size={28} color={Colors.primary} />
            <View style={styles.userInfoText}>
              <Text style={styles.userName} numberOfLines={1}>
                {displayName}
              </Text>
              <Text style={styles.userSubtext} numberOfLines={1}>
                {gymSubTitle}
              </Text>
            </View>
          </TouchableOpacity>

          {/* Menu Section Title */}
          <Text style={styles.menuHeading}>Menü</Text>

          {/* Menu Items List */}
          <ScrollView
            style={styles.menuScrollView}
            showsVerticalScrollIndicator={false}
          >
            {menuItems.map((item) => {
              const IconComp = item.icon;
              return (
                <TouchableOpacity
                  key={item.route}
                  style={styles.menuItemRow}
                  onPress={() => {
                    onClose();
                    if (onNavigate) {
                      onNavigate(item.route);
                    } else if (navigation) {
                      navigation.navigate(item.route);
                    }
                  }}
                  activeOpacity={0.7}
                >
                  <IconComp size={22} color={Colors.primary} />
                  <Text style={styles.menuItemTitle}>{item.title}</Text>
                  <ChevronRight size={20} color={Colors.primary} />
                </TouchableOpacity>
              );
            })}

            {isAdmin && (
              <TouchableOpacity
                style={styles.menuItemRow}
                onPress={() => {
                  onClose();
                  if (onNavigate) {
                    onNavigate('AdminDashboard');
                  } else if (navigation) {
                    navigation.navigate('AdminDashboard');
                  }
                }}
                activeOpacity={0.7}
              >
                <Shield size={22} color={Colors.primary} />
                <Text style={styles.menuItemTitle}>Admin Panel</Text>
                <ChevronRight size={20} color={Colors.primary} />
              </TouchableOpacity>
            )}
          </ScrollView>

          {/* Logout Button */}
          <TouchableOpacity
            style={styles.logoutButton}
            onPress={handleLogout}
            activeOpacity={0.7}
          >
            <LogOut size={20} color={Colors.textSecondaryDark} />
            <Text style={styles.logoutText}>Çıkış Yap</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    flexDirection: 'row',
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
  },
  backdrop: {
    flex: 1,
  },
  drawerSheet: {
    width: DRAWER_WIDTH,
    height: '100%',
    backgroundColor: Colors.backgroundDark,
    paddingHorizontal: 24,
    paddingTop: 50,
    paddingBottom: 24,
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
  },
  closeButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 20,
  },
  userCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 100,
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginBottom: 28,
  },
  userInfoText: {
    marginLeft: 12,
    flex: 1,
  },
  userName: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.textDark,
  },
  userSubtext: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  menuHeading: {
    fontSize: 24,
    fontWeight: '600',
    color: Colors.textDark,
    marginBottom: 16,
  },
  menuScrollView: {
    flex: 1,
  },
  menuItemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
  },
  menuItemTitle: {
    flex: 1,
    fontSize: 16,
    fontWeight: '500',
    color: Colors.textDark,
    marginLeft: 14,
  },
  logoutButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 16,
    marginTop: 8,
  },
  logoutText: {
    fontSize: 16,
    fontWeight: '500',
    color: Colors.textSecondaryDark,
    marginLeft: 12,
  },
});
