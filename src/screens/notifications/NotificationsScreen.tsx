import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  FlatList,
  ActivityIndicator,
} from 'react-native';
import { ArrowLeft, Bell, CheckCheck, Trash2, ShieldAlert } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { NotificationService, AppNotification } from '../../services/notificationService';

interface NotificationsScreenProps {
  navigation?: any;
}

export const NotificationsScreen: React.FC<NotificationsScreenProps> = ({ navigation }) => {
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(true);

  const unreadCount = notifications.filter((n) => !n.is_read).length;

  useEffect(() => {
    loadNotifications();
  }, []);

  const loadNotifications = async () => {
    setLoading(true);
    try {
      const user = await AuthService.getCurrentUser();
      if (!user) return;
      const data = await NotificationService.fetchNotifications(user.id);
      setNotifications(data);
    } catch (e) {
      console.error('Failed to load notifications:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleMarkAllRead = async () => {
    const user = await AuthService.getCurrentUser();
    if (!user) return;
    await NotificationService.markAllAsRead(user.id);
    loadNotifications();
  };

  const handleClearAll = async () => {
    const user = await AuthService.getCurrentUser();
    if (!user) return;
    await NotificationService.clearAllNotifications(user.id);
    setNotifications([]);
  };

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
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
            <TouchableOpacity style={styles.iconBtn} onPress={handleClearAll}>
              <Trash2 size={20} color={Colors.error} />
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
          <Text style={styles.emptyTitle}>Henüz bildiriminiz yok</Text>
          <Text style={styles.emptySubtitle}>Tüm bildirimleriniz burada görüntülenecektir.</Text>
        </View>
      ) : (
        <FlatList
          data={notifications}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.listContent}
          renderItem={({ item }) => (
            <View style={[styles.card, !item.is_read && styles.unreadCard]}>
              <View style={styles.cardHeader}>
                <Bell size={18} color={!item.is_read ? Colors.primary : Colors.textSecondaryDark} />
                <Text style={styles.cardTitle}>{item.title}</Text>
              </View>
              <Text style={styles.cardBody}>{item.body}</Text>
              <Text style={styles.cardTime}>
                {new Date(item.created_at).toLocaleString('tr-TR')}
              </Text>
            </View>
          )}
        />
      )}
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
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 16,
    backgroundColor: Colors.cardDark,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderDark,
  },
  backBtn: {
    marginRight: 14,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
  },
  unreadText: {
    fontSize: 12,
    color: Colors.primary,
    fontWeight: '600',
    marginTop: 2,
  },
  headerActions: {
    flexDirection: 'row',
    gap: 8,
  },
  iconBtn: {
    padding: 8,
    borderRadius: 10,
    backgroundColor: Colors.backgroundDark,
  },
  centerBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.textDark,
    marginTop: 12,
  },
  emptySubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 6,
    textAlign: 'center',
  },
  listContent: {
    padding: 20,
    gap: 12,
  },
  card: {
    backgroundColor: Colors.cardDark,
    borderRadius: 18,
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  unreadCard: {
    borderColor: Colors.primary,
    backgroundColor: 'rgba(255, 96, 71, 0.06)',
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 6,
  },
  cardTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.textDark,
    flex: 1,
  },
  cardBody: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    lineHeight: 20,
    marginBottom: 8,
  },
  cardTime: {
    fontSize: 11,
    color: Colors.textSecondaryDark,
  },
});
