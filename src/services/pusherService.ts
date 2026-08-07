import { supabase } from './supabaseClient';
import { RealtimeChannel } from '@supabase/supabase-js';

// Aktif kanalları tutmak için bir sözlük
const activeChannels: { [key: string]: RealtimeChannel } = {};

class PusherService {
  /**
   * Kullanıcıya özel olayları dinlemek için kanala abone olur
   */
  static subscribeToUser(userId: string, callback: (payload: any) => void) {
    const channelName = `user_${userId}`;

    // Eğer daha önce abone olunduysa, abonelikten çık
    if (activeChannels[channelName]) {
      this.unsubscribeFromChannel(channelName);
    }

    const channel = supabase.channel(channelName)
      .on(
        'broadcast',
        { event: 'notification' }, // Tüm kullanıcı bildirimlerini yakalamak için örnek bir event
        (payload) => {
          callback(payload);
        }
      )
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') {
          console.log(`Kullanıcı kanalına abone olundu: ${channelName}`);
        }
      });

    activeChannels[channelName] = channel;
  }

  /**
   * Salona özel canlı olayları dinlemek için kanala abone olur
   */
  static subscribeToGym(gymId: string, callback: (payload: any) => void) {
    const channelName = `gym_${gymId}`;

    if (activeChannels[channelName]) {
      this.unsubscribeFromChannel(channelName);
    }

    const channel = supabase.channel(channelName)
      .on(
        'broadcast',
        { event: 'gym_event' }, // Salon duyuruları veya giriş çıkış olayları
        (payload) => {
          callback(payload);
        }
      )
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') {
          console.log(`Salon kanalına abone olundu: ${channelName}`);
        }
      });

    activeChannels[channelName] = channel;
  }

  /**
   * Belirtilen kanaldan aboneliği kaldırır
   */
  static unsubscribeFromChannel(channelName: string) {
    const channel = activeChannels[channelName];
    if (channel) {
      supabase.removeChannel(channel).then(() => {
        console.log(`Kanaldan abonelik kaldırıldı: ${channelName}`);
      });
      delete activeChannels[channelName];
    }
  }

  /**
   * Tüm abonelikleri kaldırır
   */
  static unsubscribeAll() {
    Object.keys(activeChannels).forEach(channelName => {
      this.unsubscribeFromChannel(channelName);
    });
  }
}

export default PusherService;
