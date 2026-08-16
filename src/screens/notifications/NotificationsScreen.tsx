import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  FlatList,
  ActivityIndicator,
} from 'react-native';
import { ArrowLeft, Bell, CheckCheck, Trash2, Dumbbell, CreditCard, User, DollarSign, Clock, Shield, X } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { NotificationService, NotificationItem } from '../../services/notificationService';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const getNotifIcon = (type: string) => {
  switch (type) {
    case 'workout':    return { icon: Dumbbell,    color: '#FF6047' };
    case 'membership': return { icon: CreditCard,   color: '#9C27B0' };
    case 'coach':      return { icon: User,         color: '#2196F3' };
    case 'payment':    return { icon: DollarSign,   color: '#4CAF50' };
    case 'reminder':   return { icon: Clock,        color: '#FF9800' };
    case 'system':     return { icon: Shield,       color: '#607D8B' };
    default:           return { icon: Bell,         color: Colors.primary };
  }
};

interface NotificationsScreenProps {
  navigation?: any;
}

export const NotificationsScreen: React.FC<NotificationsScreenProps> = ({ navigation }) => {
  const insets = useSafeAreaInsets();
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(true);

  const unreadCount = notifications.filter((n) => !n.is_read).length;

  useEffect(() => {
    let unsubscribe: (() => void) | undefined;

    const setup = async () => {
      setLoading(true);
      try {
        const user = await AuthService.getCurrentUser();
        if (!user) return;
        const data = await NotificationService.fetchNotifications(user.id);
        setNotifications(data);

        unsubscribe = NotificationService.subscribeToNotifications(user.id, (payload) => {
          if (payload.eventType === 'INSERT' && payload.new) {
            setNotifications((prev) => [payload.new, ...prev.filter((n) => n.id !== payload.new.id)]);
          } else if (payload.eventType === 'UPDATE' && payload.new) {
            setNotifications((prev) => prev.map((n) => (n.id === payload.new.id ? payload.new : n)));
          } else if (payload.eventType === 'DELETE' && payload.old) {
            setNotifications((prev) => prev.filter((n) => n.id !== payload.old.id));
          }
        });
      } catch (e) {
        console.error('Failed to load notifications:', e);
      } finally {
        setLoading(false);
      }
    };

    setup();

    return () => {
      if (unsubscribe) unsubscribe();
    };
  }, []);

  const handleMarkAllRead = async () => {
    const user = await AuthService.getCurrentUser();
    if (!user) return;
    try {
      await NotificationService.markAllAsRead(user.id);
      setNotifications((prev) => prev.map((n) => ({ ...n, is_read: true })));
    } catch (e) {
      console.error('Error marking all as read:', e);
    }
  };

  const handleMarkOneRead = async (notifId: string) => {
    try {
      await NotificationService.markAsRead(notifId);
      setNotifications((prev) => prev.map((n) => (n.id === notifId ? { ...n, is_read: true } : n)));
    } catch (e) {
      console.error('Error marking as read:', e);
    }
  };

  const handleDeleteNotification = async (notifId: string) => {
    try {
      await NotificationService.deleteNotification(notifId);
      setNotifications((prev) => prev.filter((n) => n.id !== notifId));
    } catch (e) {
      console.error('Bildirim silinirken hata:', e);
    }
  };

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <View style={{ flex: 1 }}>
          <Text style={styles.headerTitle}>Bildirimler</Text>
          {unreadCount > 0 && (
            <Text style={styles.unreadText}>{unreadCount} okunmamış bildirim</Text>
          )}
        </View>

        {notifications.length > 0 && (
          <View style={styles.headerActions}>
            <TouchableOpacity style={styles.iconBtn} onPress={handleMarkAllRead}>
              <CheckCheck size={20} color={Colors.primary} />
            </TouchableOpacity>
          </View>
        )}
      </View>

      {/* Main List */}
      {loading ? (
        <View style={styles.centerBox}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : notifications.length === 0 ? (
        <View style={styles.centerBox}>
          <Bell size={48} color={Colors.textSecondaryDark} />
          <Text style={styles.emptyTitle}>Bildiriminiz Bulunmuyor</Text>
          <Text style={styles.emptySubtitle}>
            Antrenman, üyelik ve randevu güncellemeleri burada görünecektir.
          </Text>
        </View>
      ) : (
        <FlatList
          data={notifications}
          keyExtractor={(item) => item.id}
          contentContainerStyle={[styles.listContent, { paddingBottom: 24 + insets.bottom }]}
          renderItem={({ item }) => {
            const { icon: IconComponent, color: iconColor } = getNotifIcon(item.type || 'system');
            return (
              <TouchableOpacity
                activeOpacity={0.8}
                onPress={() => !item.is_read && handleMarkOneRead(item.id)}
                style={[styles.card, !item.is_read && styles.unreadCard]}
              >
                <View style={styles.cardHeader}>
                  <IconComponent size={18} color={!item.is_read ? iconColor : Colors.textSecondaryDark} />
                  <Text style={styles.cardTitle}>{item.title}</Text>
                  <TouchableOpacity onPress={() => handleDeleteNotification(item.id)} style={{ padding: 4 }}>
                    <X size={18} color={Colors.textSecondaryDark} />
                  </TouchableOpacity>
                </View>
                <Text style={styles.cardBody}>{item.message}</Text>
                <Text style={styles.cardTime}>
                  {new Date(item.created_at).toLocaleDateString('tr-TR', {
                    day: 'numeric',
                    month: 'short',
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </Text>
              </TouchableOpacity>
            );
          }}
        />
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#1E293B',
    gap: 12,
  },
  backBtn: {
    padding: 4,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  unreadText: {
    fontSize: 12,
    color: Colors.primary,
    fontWeight: '500',
  },
  headerActions: {
    flexDirection: 'row',
    gap: 8,
  },
  iconBtn: {
    padding: 8,
    borderRadius: 8,
    backgroundColor: '#1E293B',
  },
  listContent: {
    padding: 16,
    gap: 12,
  },
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: '#334155',
  },
  unreadCard: {
    borderColor: Colors.primary,
    backgroundColor: '#1e293b',
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 6,
  },
  cardTitle: {
    flex: 1,
    fontSize: 14,
    fontWeight: '600',
    color: Colors.textDark,
  },
  cardBody: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    lineHeight: 18,
    marginBottom: 8,
  },
  cardTime: {
    fontSize: 11,
    color: '#64748B',
  },
  centerBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
    gap: 12,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.textDark,
  },
  emptySubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    textAlign: 'center',
    lineHeight: 18,
  },
});
