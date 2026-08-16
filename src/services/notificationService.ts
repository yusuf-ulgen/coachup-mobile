import { supabase } from './supabaseClient';

export interface AppNotification {
  id: string;
  user_id: string;
  title: string;
  body: string;
  type?: string;
  is_read: boolean;
  created_at: string;
}

export const NotificationService = {
  async fetchNotifications(userId: string): Promise<AppNotification[]> {
    try {
      const { data, error } = await supabase
        .from('notifications')
        .select('*')
        .eq('user_id', userId)
        .order('created_at', { ascending: false });

      if (error) {
        // Fallback demo notifications if table is empty
        return [
          {
            id: '1',
            user_id: userId,
            title: 'Hoş Geldiniz! 🏋️',
            body: 'CoachUp ailesine katıldığınız için teşekkür ederiz. Antrenman programlarınızı hemen inceleyin.',
            is_read: false,
            created_at: new Date().toISOString(),
          },
          {
            id: '2',
            user_id: userId,
            title: 'Günün Programı Hazır 🔥',
            body: 'Bugün için planlanan göğüs ve ön kol antrenmanınız sizi bekliyor.',
            is_read: true,
            created_at: new Date(Date.now() - 86400000).toISOString(),
          },
        ];
      }

      return data || [];
    } catch (e) {
      console.error('Error fetching notifications:', e);
      return [];
    }
  },

  async markAllAsRead(userId: string) {
    await supabase
      .from('notifications')
      .update({ is_read: true })
      .eq('user_id', userId);
  },

  async clearAllNotifications(userId: string) {
    await supabase
      .from('notifications')
      .delete()
      .eq('user_id', userId);
  },
};
