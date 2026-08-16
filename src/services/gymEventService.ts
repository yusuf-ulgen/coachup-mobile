import { supabase } from './supabaseClient';

export interface GymEvent {
  id: string;
  gym_id?: string;
  title: string;
  description?: string;
  event_date: string;
  start_time: string;
  end_time: string;
  capacity?: number;
}

export interface EventParticipant {
  id: string;
  event_id: string;
  user_id: string;
  status: 'registered' | 'waiting' | 'cancelled' | string;
}

export const GymEventService = {
  async fetchEventsForDate(dateStr: string): Promise<GymEvent[]> {
    try {
      const { data, error } = await supabase
        .from('gym_events')
        .select('*')
        .eq('event_date', dateStr);

      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching gym events:', e);
      return [];
    }
  },

  async fetchParticipationsForDate(userId: string, _dateStr: string): Promise<EventParticipant[]> {
    try {
      const { data, error } = await supabase
        .from('event_participants')
        .select('*')
        .eq('user_id', userId)
        .neq('status', 'cancelled');

      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching event participations:', e);
      return [];
    }
  },

  async joinEvent(userId: string, eventId: string): Promise<string> {
    const { data, error } = await supabase.rpc('atomic_join_event', {
      p_user_id: userId,
      p_event_id: eventId,
    });

    if (error) {
      throw new Error(error.message || 'Etkinlik kaydı yapılamadı.');
    }

    if (data && data.success === false) {
      throw new Error(data.error || 'Etkinlik kaydı yapılamadı.');
    }

    return data?.status || 'registered';
  },

  async leaveEvent(userId: string, participantId: string): Promise<void> {
    const { data, error } = await supabase.rpc('atomic_leave_event', {
      p_user_id: userId,
      p_participant_id: participantId,
    });

    if (error) {
      throw new Error(error.message || 'Etkinlik kaydı iptal edilemedi.');
    }

    if (data && data.success === false) {
      throw new Error(data.error || 'Etkinlik kaydı iptal edilemedi.');
    }
  },
};
