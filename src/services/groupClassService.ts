import { supabase } from './supabaseClient';

export interface GroupClass {
  id: string;
  gym_id: string;
  name: string;
  description?: string;
  instructor_name?: string;
  start_time: string;
  end_time: string;
  capacity?: number;
  date_str?: string;
  day_of_week?: number;
}

export interface ClassBooking {
  id: string;
  class_id: string;
  user_id: string;
  created_at?: string;
  status?: string;
  is_waitlist?: boolean;
  group_class?: GroupClass;
}

export const GroupClassService = {
  async fetchBookingsForDate(userId: string, _dateStr: string): Promise<ClassBooking[]> {
    try {
      const { data, error } = await supabase
        .from('class_bookings')
        .select('*, group_class:group_classes(*)')
        .eq('user_id', userId)
        .neq('status', 'cancelled');

      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching class bookings:', e);
      return [];
    }
  },

  async fetchAvailableGroupClasses(gymId: string): Promise<GroupClass[]> {
    try {
      const { data, error } = await supabase
        .from('group_classes')
        .select('*')
        .eq('gym_id', gymId)
        .order('start_time', { ascending: true });

      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching group classes:', e);
      return [];
    }
  },

  async fetchClassesForDate(userId: string, dateStr: string): Promise<GroupClass[]> {
    try {
      const { data: userProfile } = await supabase.from('users').select('gym_id').eq('id', userId).single();
      if (!userProfile?.gym_id) return [];
      
      const { data, error } = await supabase
        .from('group_classes')
        .select('*')
        .eq('gym_id', userProfile.gym_id)
        .eq('is_active', true)
        .order('start_time', { ascending: true });

      if (error) throw error;
      if (!data) return [];

      const targetDay = new Date(dateStr).getDay();
      return data.filter((c: any) => c.day_of_week === undefined || c.day_of_week === null || c.day_of_week === targetDay);
    } catch (e) {
      console.error('Error fetching classes for date:', e);
      return [];
    }
  },

  async bookClass(userId: string, classId: string, bookingDate?: string) {
    const targetDate = bookingDate || new Date().toISOString().split('T')[0];
    
    // Call atomic RPC
    const { data, error } = await supabase.rpc('atomic_book_group_class', {
      p_user_id: userId,
      p_class_id: classId,
      p_booking_date: targetDate,
    });

    if (error) {
      // Direct insert fallback if RPC is not deployed yet
      const { data: directData, error: directErr } = await supabase.from('class_bookings').insert({
        user_id: userId,
        class_id: classId,
        status: 'booked',
      }).select().single();
      if (directErr) throw directErr;
      return directData;
    }

    if (data && !data.success) {
      throw new Error(data.error || 'Ders rezervasyonu oluşturulamadı.');
    }

    return data;
  },

  async cancelBooking(userId: string, bookingId: string) {
    const { data, error } = await supabase.rpc('atomic_cancel_group_class', {
      p_user_id: userId,
      p_booking_id: bookingId,
    });

    if (error) {
      const { data: directData, error: directErr } = await supabase
        .from('class_bookings')
        .update({ status: 'cancelled' })
        .eq('id', bookingId)
        .select()
        .single();
      if (directErr) throw directErr;
      return directData;
    }

    if (data && !data.success) {
      throw new Error(data.error || 'Rezervasyon iptal edilemedi.');
    }

    return data;
  },
};
