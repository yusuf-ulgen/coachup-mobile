import React, { useEffect, useState } from 'react';
import { View, TouchableOpacity, StyleSheet, Image } from 'react-native';
import { Menu, Bell } from 'lucide-react-native';
import { Colors } from '../theme/colors';
import { GYM_CONFIG } from '../config/gym';
import { AuthService } from '../services/authService';
import { NotificationService } from '../services/notificationService';

interface HeaderProps {
  navigation?: any;
  onOpenDrawer?: () => void;
}

export const Header: React.FC<HeaderProps> = ({ navigation, onOpenDrawer }) => {
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    const checkUnread = async () => {
      try {
        const user = await AuthService.getCurrentUser();
        if (user) {
          const notifications = await NotificationService.fetchNotifications(user.id);
          setUnreadCount(notifications.filter((n) => !n.is_read).length);
        }
      } catch (e) {
        console.error('Header notification check error:', e);
      }
    };
    checkUnread();
  }, []);

  return (
    <View style={styles.headerContainer}>
      <TouchableOpacity style={styles.iconButton} onPress={onOpenDrawer} activeOpacity={0.8}>
        <Menu size={20} color={Colors.textDark} />
      </TouchableOpacity>

      <Image
        source={GYM_CONFIG.LOGIN_LOGO}
        style={styles.headerLogo}
        resizeMode="contain"
      />

      <TouchableOpacity
        style={styles.iconButton}
        onPress={() => navigation?.navigate('Notifications')}
        activeOpacity={0.8}
      >
        <Bell size={20} color={Colors.textDark} />
        {unreadCount > 0 && <View style={styles.badgeDot} />}
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  headerContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 12,
    backgroundColor: Colors.cardDark,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderDark,
  },
  iconButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: Colors.backgroundDark,
    justifyContent: 'center',
    alignItems: 'center',
    position: 'relative',
  },
  headerLogo: {
    height: 32,
    width: 120,
  },
  badgeDot: {
    position: 'absolute',
    top: 8,
    right: 8,
    width: 9,
    height: 9,
    borderRadius: 5,
    backgroundColor: Colors.primary,
  },
});
