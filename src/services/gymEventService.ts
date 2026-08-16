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
    try {
      const { data, error } = await supabase.rpc('atomic_join_event', {
        p_user_id: userId,
        p_event_id: eventId,
      });

      if (!error && data) {
        if (data.success === false) {
          throw new Error(data.error || 'Etkinlik kaydı yapılamadı.');
        }
        return data?.status || 'registered';
      }
    } catch (rpcErr: any) {
      if (
        rpcErr.message &&
        !rpcErr.message.includes('Could not find the function') &&
        !rpcErr.message.includes('schema cache')
      ) {
        throw rpcErr;
      }
      console.warn('RPC atomic_join_event exception, attempting direct fallback:', rpcErr);
    }

    // Direct fallback
    const { data: existing } = await supabase
      .from('event_participants')
      .select('id, status')
      .eq('event_id', eventId)
      .eq('user_id', userId)
      .neq('status', 'cancelled')
      .maybeSingle();

    if (existing) {
      return existing.status || 'registered';
    }

    const { data: inserted, error: insertError } = await supabase
      .from('event_participants')
      .insert({
        event_id: eventId,
        user_id: userId,
        status: 'registered',
      })
      .select()
      .single();

    if (insertError) {
      throw new Error(insertError.message || 'Etkinlik kaydı yapılamadı.');
    }

    return 'registered';
  },

  async leaveEvent(userId: string, participantId: string): Promise<void> {
    try {
      const { data, error } = await supabase.rpc('atomic_leave_event', {
        p_user_id: userId,
        p_participant_id: participantId,
      });

      if (!error && data) {
        if (data.success === false) {
          throw new Error(data.error || 'Etkinlik kaydı iptal edilemedi.');
        }
        return;
      }
    } catch (rpcErr: any) {
      if (
        rpcErr.message &&
        !rpcErr.message.includes('Could not find the function') &&
        !rpcErr.message.includes('schema cache')
      ) {
        throw rpcErr;
      }
      console.warn('RPC atomic_leave_event exception, attempting direct fallback:', rpcErr);
    }

    // Direct fallback
    const { error: updateError } = await supabase
      .from('event_participants')
      .update({ status: 'cancelled' })
      .eq('id', participantId);

    if (updateError) {
      throw new Error(updateError.message || 'Etkinlik kaydı iptal edilemedi.');
    }
  },
};
