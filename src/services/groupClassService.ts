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
  group_class?: GroupClass;
}

export const GroupClassService = {
  async fetchBookingsForDate(userId: string, dateStr: string): Promise<ClassBooking[]> {
    try {
      const { data, error } = await supabase
        .from('class_bookings')
        .select('*, group_class:group_classes(*)')
        .eq('user_id', userId);

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

  async bookClass(userId: string, classId: string) {
    const { data, error } = await supabase.from('class_bookings').insert({
      user_id: userId,
      class_id: classId,
      status: 'booked',
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
