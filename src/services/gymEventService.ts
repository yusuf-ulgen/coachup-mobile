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
  status: string;
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

  async fetchParticipationsForDate(userId: string, dateStr: string): Promise<EventParticipant[]> {
    try {
      const { data, error } = await supabase
        .from('gym_event_participants')
        .select('*')
        .eq('user_id', userId);

      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching event participations:', e);
      return [];
    }
  },

  async joinEvent(userId: string, eventId: string): Promise<string> {
    try {
      const { data, error } = await supabase
        .from('gym_event_participants')
        .insert({
          event_id: eventId,
          user_id: userId,
          status: 'confirmed',
        })
        .select()
        .single();

      if (error) throw error;
      return data?.status || 'confirmed';
    } catch (e) {
      console.error('Error joining gym event:', e);
      throw e;
    }
  },

  async leaveEvent(participantId: string): Promise<void> {
    try {
      const { error } = await supabase
        .from('gym_event_participants')
        .delete()
        .eq('id', participantId);

      if (error) throw error;
    } catch (e) {
      console.error('Error leaving gym event:', e);
      throw e;
    }
  },
};
