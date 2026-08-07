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
}

export interface ClassBooking {
  id: string;
  class_id: string;
  user_id: string;
  booked_at?: string;
  status?: string;
  is_waitlist?: boolean;
  group_class?: GroupClass;
}

export const GroupClassService = {
  async fetchBookingsForDate(userId: string, dateStr: string): Promise<ClassBooking[]> {
    try {
      const { data, error } = await supabase
        .from('class_bookings')
        .select('*, group_class:group_classes(*)')
        .eq('user_id', userId)
        .eq('booking_date', dateStr)
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

  async bookClass(userId: string, classId: string, bookingDate?: string, status: string = 'booked') {
    const targetDate = bookingDate || new Date().toISOString().split('T')[0];
    const { data, error } = await supabase.from('class_bookings').insert({
      user_id: userId,
      class_id: classId,
      booking_date: targetDate,
      status: status,
    });

    if (error) throw error;
    return data;
  },

  async cancelBooking(bookingId: string) {
    const { data, error } = await supabase
      .from('class_bookings')
      .delete()
      .eq('id', bookingId);

    if (error) throw error;
    return data;
  },
};
