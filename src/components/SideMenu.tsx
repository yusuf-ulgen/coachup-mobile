import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Dimensions,
} from 'react-native';
import { SmoothModal } from './motion/SmoothModal';
import { feedback } from '../services/feedbackService';
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
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { AuthService } from '../services/authService';
import { UserService } from '../services/userService';

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

interface MenuItemData {
  title: string;
  icon: any;
  route: string;
  salonOnly?: boolean; // Sadece salon üyelerine göster
}

const menuItems: MenuItemData[] = [
  { title: 'Aktivite Geçmişi', icon: BarChart2, route: 'PersonalRecords' },
  { title: 'Randevular', icon: Calendar, route: 'Appointments', salonOnly: true },
  { title: 'Grup Dersleri', icon: Users, route: 'GroupClasses', salonOnly: true },
  { title: 'Beslenme', icon: BookOpen, route: 'Nutrition' },
  { title: 'İlerleme', icon: BarChart2, route: 'Progress' },
  { title: 'Hedefler', icon: Target, route: 'Goals' },
  { title: 'Üyelik', icon: CreditCard, route: 'Membership' },
  { title: 'Ödemeler', icon: DollarSign, route: 'Payments', salonOnly: true },
  { title: 'Anketler', icon: FileText, route: 'Surveys' },
  { title: 'Rezervasyon', icon: MapPin, route: 'Reservations', salonOnly: true },
  { title: 'Koçlar', icon: Dumbbell, route: 'Coaches', salonOnly: true },
  { title: 'Ayarlar', icon: Settings, route: 'Settings' },
];

export const SideMenu: React.FC<SideMenuProps> = ({
  visible,
  onClose,
  userProfile,
  onNavigate,
  navigation,
}) => {
  const insets = useSafeAreaInsets();
  const [hasActiveMembership, setHasActiveMembership] = useState(false);

  React.useEffect(() => {
    if (visible && userProfile) {
      UserService.hasActiveMembership(userProfile)
        .then(setHasActiveMembership)
        .catch(() => setHasActiveMembership(false));
    }
  }, [visible, userProfile]);

  const handleLogout = async () => {
    const confirmed = await feedback.destructive({
      title: 'Çıkış Yap',
      message: 'Hesabınızdan çıkış yapmak istediğinize emin misiniz?',
      confirmText: 'Çıkış Yap',
      cancelText: 'İptal',
    });

    if (confirmed) {
      onClose();
      try {
        await AuthService.signOut();
      } catch (e) {
        console.error('Logout error:', e);
      }
    }
  };

  const displayName = userProfile
    ? `${userProfile.name || ''} ${userProfile.surname || ''}`.trim() || 'Kullanıcı'
    : 'Kullanıcı';

  const gymSubTitle = userProfile?.is_individual
    ? 'Bireysel'
    : userProfile?.gym_name || 'Bağlı Salon Yok';

  const isAdmin = userProfile?.role === 'admin' || userProfile?.role === 'gym_manager';

  return (
    <SmoothModal
      visible={visible}
      onClose={onClose}
      variant="drawer-left"
      drawerWidth={DRAWER_WIDTH}
    >
      {/* Drawer Sheet Content */}
      <View
        style={[
          styles.drawerSheet,
          {
            paddingTop: Math.max(12, insets.top + 8),
            paddingBottom: Math.max(16, insets.bottom + 12),
          },
        ]}
      >
        <View style={{ flex: 1 }}>
          {/* Top Header Row: User Info (Left) & Close Button (Right) */}
          <View style={styles.topHeaderRow}>
            <TouchableOpacity
              style={styles.userCard}
              onPress={() => {
                onClose();
                if (onNavigate) onNavigate('Profile');
                else if (navigation) navigation.navigate('Profile');
              }}
              activeOpacity={0.8}
            >
              <User size={24} color={Colors.primary} />
              <View style={styles.userInfoText}>
                <Text style={styles.userName} numberOfLines={1}>
                  {displayName}
                </Text>
                <Text style={styles.userSubtext} numberOfLines={1}>
                  {gymSubTitle}
                </Text>
              </View>
            </TouchableOpacity>

            <TouchableOpacity
              style={styles.closeButton}
              onPress={onClose}
              activeOpacity={0.8}
            >
              <X size={18} color={Colors.textDark} />
            </TouchableOpacity>
          </View>

          {/* Menu Section Title */}
          <Text style={styles.menuHeading}>Menü</Text>

          {/* Menü Öğeleri Listesi */}
          <ScrollView
            style={styles.menuScrollView}
            showsVerticalScrollIndicator={false}
          >
            {menuItems
              .filter((item) => {
                // Sadece aktif salon üyelerine salon-only menü göster
                if (item.salonOnly && (!hasActiveMembership || userProfile?.is_individual)) return false;
                return true;
              })
              .map((item) => {
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
                  <IconComp size={20} color={Colors.primary} />
                  <Text style={styles.menuItemTitle}>{item.title}</Text>
                  <ChevronRight size={18} color={Colors.primary} />
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
                <Shield size={20} color={Colors.primary} />
                <Text style={styles.menuItemTitle}>Admin Panel</Text>
                <ChevronRight size={18} color={Colors.primary} />
              </TouchableOpacity>
            )}
          </ScrollView>
        </View>

        {/* Logout Button Pinned to Bottom */}
        <TouchableOpacity
          style={styles.logoutButton}
          onPress={handleLogout}
          activeOpacity={0.7}
        >
          <LogOut size={20} color={Colors.textSecondaryDark} />
          <Text style={styles.logoutText}>Çıkış Yap</Text>
        </TouchableOpacity>
      </View>
    </SmoothModal>
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
    paddingHorizontal: 20,
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    flexDirection: 'column',
    justifyContent: 'space-between',
  },
  topHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  userCard: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 100,
    paddingHorizontal: 14,
    paddingVertical: 10,
    marginRight: 10,
  },
  closeButton: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  userInfoText: {
    marginLeft: 10,
    flex: 1,
  },
  userName: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textDark,
  },
  userSubtext: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 1,
  },
  menuHeading: {
    fontSize: 20,
    fontWeight: '600',
    color: Colors.textDark,
    marginBottom: 12,
  },
  menuScrollView: {
    flex: 1,
  },
  menuItemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
  },
  menuItemTitle: {
    flex: 1,
    fontSize: 15,
    fontWeight: '500',
    color: Colors.textDark,
    marginLeft: 12,
  },
  logoutButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255, 255, 255, 0.08)',
    marginTop: 2,
  },
  logoutText: {
    fontSize: 15,
    fontWeight: '500',
    color: Colors.textSecondaryDark,
    marginLeft: 10,
  },
});
