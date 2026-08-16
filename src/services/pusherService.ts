import { supabase } from './supabaseClient';
import { RealtimeChannel } from '@supabase/supabase-js';

const activeChannels: { [key: string]: RealtimeChannel } = {};

/**
 * PusherService - Migrated to canonical Supabase Realtime Channels.
 * Provides backward compatible signature while using Postgres Realtime.
 */
class PusherService {
  static subscribeToUser(userId: string, callback: (payload: any) => void) {
    const channelName = `notifications:user:${userId}`;

    if (activeChannels[channelName]) {
      this.unsubscribeFromChannel(channelName);
    }

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
          callback(payload);
        }
      )
      .subscribe();

    activeChannels[channelName] = channel;
  }

  static subscribeToGym(gymId: string, callback: (payload: any) => void) {
    const channelName = `gym:${gymId}:events`;

    if (activeChannels[channelName]) {
      this.unsubscribeFromChannel(channelName);
    }

    const channel = supabase
      .channel(channelName)
      .on(
        'postgres_changes',
        {
          event: '*',
          schema: 'public',
          table: 'gym_events',
          filter: `gym_id=eq.${gymId}`,
        },
        (payload) => {
          callback(payload);
        }
      )
      .subscribe();

    activeChannels[channelName] = channel;
  }

  static unsubscribeFromChannel(channelName: string) {
    const channel = activeChannels[channelName];
    if (channel) {
      supabase.removeChannel(channel).then(() => {});
      delete activeChannels[channelName];
    }
  }

  static unsubscribeAll() {
    Object.keys(activeChannels).forEach((channelName) => {
      this.unsubscribeFromChannel(channelName);
    });
  }
}

export default PusherService;
