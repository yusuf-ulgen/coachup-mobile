import { supabase } from './supabaseClient';
import { RealtimeChannel } from '@supabase/supabase-js';

export interface NotificationItem {
  id: string;
  user_id: string;
  gym_id?: string;
  title: string;
  message: string;
  type: string;
  is_read: boolean;
  action_url?: string;
  created_at: string;
}

let activeNotificationChannel: RealtimeChannel | null = null;

export const NotificationService = {
  async fetchNotifications(userId: string): Promise<NotificationItem[]> {
    try {
      const { data, error } = await supabase
        .from('notifications')
        .select('*')
        .eq('user_id', userId)
        .order('created_at', { ascending: false });

      if (error) {
        console.error('Error fetching notifications:', error);
        return [];
      }
      return data || [];
    } catch (e) {
      console.error('Error in fetchNotifications:', e);
      return [];
    }
  },

  async markAsRead(notificationId: string): Promise<void> {
    try {
      const { error } = await supabase
        .from('notifications')
        .update({ is_read: true })
        .eq('id', notificationId);

      if (error) throw error;
    } catch (e) {
      console.error('Error in markAsRead:', e);
      throw e;
    }
  },

  async markAllAsRead(userId: string): Promise<void> {
    try {
      const { error } = await supabase
        .from('notifications')
        .update({ is_read: true })
        .eq('user_id', userId)
        .eq('is_read', false);

      if (error) throw error;
    } catch (e) {
      console.error('Error in markAllAsRead:', e);
      throw e;
    }
  },

  async deleteNotification(notificationId: string): Promise<void> {
    try {
      const { error } = await supabase
        .from('notifications')
        .delete()
        .eq('id', notificationId);

      if (error) throw error;
    } catch (e) {
      console.error('Error in deleteNotification:', e);
      throw e;
    }
  },

  /**
   * Subscribes to realtime postgres changes on the notifications table for this user.
   */
  subscribeToNotifications(userId: string, onPayload: (payload: any) => void): () => void {
    if (activeNotificationChannel) {
      supabase.removeChannel(activeNotificationChannel);
      activeNotificationChannel = null;
    }

    const channelName = `notifications:user:${userId}`;
    const channel = supabase
      .channel(channelName)
      .on(
        'postgres_changes',
        {
          event: '*',
          schema: 'public',
          table: 'notifications',
          filter: `user_id=eq.${userId}`,
        },
        (payload) => {
          onPayload(payload);
        }
      )
      .subscribe();

    activeNotificationChannel = channel;

    return () => {
      if (channel) {
        supabase.removeChannel(channel);
      }
      if (activeNotificationChannel === channel) {
        activeNotificationChannel = null;
      }
    };
  },
};
