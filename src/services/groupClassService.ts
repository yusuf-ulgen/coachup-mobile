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

// Enriched item for HomeScreen - user's booked class with status info
export interface BookedClassItem {
  bookingId: string;
  classId: string;
  name: string;
  instructorName?: string;
  startTime?: string;
  endTime?: string;
  status: string;
  isPast: boolean;
  isWaitlist: boolean;
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
      throw new Error(error.message || 'Ders rezervasyonu gerçekleştirilemedi.');
    }

    if (data && data.success === false) {
      throw new Error(data.error || 'Ders rezervasyonu gerçekleştirilemedi.');
    }

    return data;
  },

  async cancelBooking(bookingId: string, userId?: string) {
    const { data, error } = await supabase.rpc('atomic_cancel_group_class', {
      p_user_id: userId || (await supabase.auth.getUser()).data.user?.id,
      p_booking_id: bookingId,
    });

    if (error) {
      throw new Error(error.message || 'Rezervasyon iptal edilemedi.');
    }

    if (data && data.success === false) {
      throw new Error(data.error || 'Rezervasyon iptal edilemedi.');
    }

    return data;
  },

  // Fetch only the classes the user has booked for a specific date,
  // enriched with isPast and isWaitlist status — mirrors CalendarScreen logic.
  async fetchBookedClassesForDate(userId: string, dateStr: string): Promise<BookedClassItem[]> {
    try {
      const { data, error } = await supabase
        .from('class_bookings')
        .select('*, group_class:group_classes(*)')
        .eq('user_id', userId)
        .neq('status', 'cancelled');

      if (error) throw error;
      if (!data) return [];

      const now = new Date();
      const targetDayOfWeek = new Date(dateStr).getDay();

      // Filter by date: prefer date_str match, fallback to day_of_week
      const filtered = data.filter((b: any) => {
        const gc = b.group_class;
        if (!gc) return false;
        if (gc.date_str) return gc.date_str === dateStr;
        return (
          gc.day_of_week === undefined ||
          gc.day_of_week === null ||
          gc.day_of_week === targetDayOfWeek
        );
      });

      return filtered.map((b: any) => {
        const gc = b.group_class;
        const statusStr = (b.status || '').toLowerCase();
        const isWaitlist = statusStr === 'waitlist' || statusStr === 'waiting';

        // Calculate whether the class end time has passed
        const endTimeStr = gc?.end_time || gc?.start_time || '23:59';
        const [endH, endM] = endTimeStr.split(':');
        const classEndTime = new Date(
          `${dateStr}T${String(endH).padStart(2, '0')}:${String(endM).padStart(2, '0')}:00`
        );
        const isPast = classEndTime < now;

        return {
          bookingId: b.id,
          classId: b.class_id,
          name: gc?.name || 'Grup Dersi',
          instructorName: gc?.instructor_name,
          startTime: gc?.start_time ? gc.start_time.substring(0, 5) : undefined,
          endTime: gc?.end_time ? gc.end_time.substring(0, 5) : undefined,
          status: b.status || 'confirmed',
          isPast,
          isWaitlist,
        };
      });
    } catch (e) {
      console.error('Error fetching booked classes for date:', e);
      return [];
    }
  },
};

