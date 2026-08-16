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
      if (!data || data.length === 0) return [];

      const targetDay = new Date(dateStr).getDay();
      const filtered = data.filter((c: any) => c.day_of_week === undefined || c.day_of_week === null || c.day_of_week === targetDay);

      if (filtered.length === 0) return [];

      // Calculate dynamic participant counts from class_bookings
      const classIds = filtered.map((c: any) => c.id);
      const { data: bookingsData } = await supabase
        .from('class_bookings')
        .select('class_id, status, booking_date')
        .in('class_id', classIds)
        .in('status', ['booked', 'confirmed']);

      const countMap: Record<string, number> = {};
      (bookingsData || []).forEach((b: any) => {
        if (!b.booking_date || b.booking_date === dateStr) {
          countMap[b.class_id] = (countMap[b.class_id] || 0) + 1;
        }
      });

      return filtered.map((c: any) => ({
        ...c,
        enrolled_count: countMap[c.id] || 0,
        current_participants: countMap[c.id] || 0,
      }));
    } catch (e) {
      console.error('Error fetching classes for date:', e);
      return [];
    }
  },

  async bookClass(userId: string, classId: string, bookingDate?: string) {
    const targetDate = bookingDate || new Date().toISOString().split('T')[0];
    
    // 1. Try atomic RPC first
    try {
      const { data, error } = await supabase.rpc('atomic_book_group_class', {
        p_user_id: userId,
        p_class_id: classId,
        p_booking_date: targetDate,
      });

      if (!error && data) {
        if (data.success === false) {
          throw new Error(data.error || 'Ders rezervasyonu gerçekleştirilemedi.');
        }
        return data;
      }

      if (error && !error.message?.includes('Could not find the function') && !error.message?.includes('schema cache')) {
        console.warn('RPC atomic_book_group_class error, attempting direct fallback:', error);
      }
    } catch (rpcErr: any) {
      if (
        rpcErr.message &&
        !rpcErr.message.includes('Could not find the function') &&
        !rpcErr.message.includes('schema cache')
      ) {
        throw rpcErr;
      }
      console.warn('RPC atomic_book_group_class exception, attempting direct fallback:', rpcErr);
    }

    // 2. Direct fallback booking logic
    // Check capacity
    const { data: cls } = await supabase
      .from('group_classes')
      .select('capacity')
      .eq('id', classId)
      .single();

    const capacity = cls?.capacity || 20;

    // Count active participants (excluding cancelled)
    const { count } = await supabase
      .from('class_bookings')
      .select('*', { count: 'exact', head: true })
      .eq('class_id', classId)
      .in('status', ['booked', 'confirmed'])
      .or(`booking_date.eq.${targetDate},booking_date.is.null`);

    const activeCount = count || 0;
    const isWaiting = activeCount >= capacity;
    const targetStatus = isWaiting ? 'waitlist' : 'confirmed';

    // Check if any booking row exists for this user and class (active or cancelled)
    const { data: existingBooking } = await supabase
      .from('class_bookings')
      .select('id, status, booking_date')
      .eq('class_id', classId)
      .eq('user_id', userId)
      .or(`booking_date.eq.${targetDate},booking_date.is.null`)
      .order('created_at', { ascending: false })
      .limit(1)
      .maybeSingle();

    if (existingBooking) {
      const currentStatus = (existingBooking.status || '').toLowerCase();
      // If already active in class or waiting list
      if (['booked', 'confirmed', 'waiting', 'waitlist'].includes(currentStatus)) {
        return {
          success: true,
          booking_id: existingBooking.id,
          status: currentStatus,
          is_waiting: currentStatus === 'waiting' || currentStatus === 'waitlist',
        };
      }

      // If previously cancelled, re-activate the existing row (prevents unique constraint error)
      const { data: reactivated, error: updateError } = await supabase
        .from('class_bookings')
        .update({
          status: targetStatus,
          booking_date: targetDate,
          created_at: new Date().toISOString(),
        })
        .eq('id', existingBooking.id)
        .select()
        .single();

      if (updateError) {
        throw new Error(updateError.message || 'Ders rezervasyonu gerçekleştirilemedi.');
      }

      return {
        success: true,
        booking_id: reactivated.id,
        status: targetStatus,
        is_waiting: isWaiting,
      };
    }

    // Insert new booking with conflict handling
    const { data: inserted, error: insertError } = await supabase
      .from('class_bookings')
      .insert({
        class_id: classId,
        user_id: userId,
        status: targetStatus,
        booking_date: targetDate,
      })
      .select()
      .single();

    if (insertError) {
      // If unique constraint violation occurs, update the existing row
      if (
        insertError.code === '23505' ||
        insertError.message?.includes('unique constraint') ||
        insertError.message?.includes('duplicate key')
      ) {
        const { data: updatedOnConflict, error: conflictUpdateErr } = await supabase
          .from('class_bookings')
          .update({
            status: targetStatus,
            booking_date: targetDate,
            created_at: new Date().toISOString(),
          })
          .eq('class_id', classId)
          .eq('user_id', userId)
          .select()
          .single();

        if (!conflictUpdateErr && updatedOnConflict) {
          return {
            success: true,
            booking_id: updatedOnConflict.id,
            status: targetStatus,
            is_waiting: isWaiting,
          };
        }
      }
      throw new Error(insertError.message || 'Ders rezervasyonu gerçekleştirilemedi.');
    }

    return {
      success: true,
      booking_id: inserted.id,
      status: targetStatus,
      is_waiting: isWaiting,
    };
  },

  async cancelBooking(bookingId: string, userId?: string) {
    const currentUserId = userId || (await supabase.auth.getUser()).data.user?.id;
    
    // 1. Try atomic RPC first
    try {
      const { data, error } = await supabase.rpc('atomic_cancel_group_class', {
        p_user_id: currentUserId,
        p_booking_id: bookingId,
      });

      if (!error && data?.success !== false) {
        return data;
      }

      if (data && data.success === false) {
        throw new Error(data.error || 'Rezervasyon iptal edilemedi.');
      }

      if (error) {
        console.warn('RPC atomic_cancel_group_class error, attempting direct fallback:', error);
      }
    } catch (rpcErr: any) {
      console.warn('RPC atomic_cancel_group_class exception, attempting direct fallback:', rpcErr);
    }

    // 2. Direct fallback update
    const { data: updateData, error: updateError } = await supabase
      .from('class_bookings')
      .update({ status: 'cancelled' })
      .eq('id', bookingId)
      .select()
      .single();

    if (updateError) {
      throw new Error(updateError.message || 'Rezervasyon iptal edilemedi.');
    }

    return { success: true, booking: updateData };
  },

  // Fetch only the classes the user has booked for a specific date,
  // enriched with isPast and isWaitlist status — mirrors CalendarScreen logic and deduplicates per class.
  async fetchBookedClassesForDate(userId: string, dateStr: string): Promise<BookedClassItem[]> {
    try {
      const { data, error } = await supabase
        .from('class_bookings')
        .select('*, group_class:group_classes(*)')
        .eq('user_id', userId)
        .neq('status', 'cancelled')
        .order('created_at', { ascending: false });

      if (error) throw error;
      if (!data) return [];

      const now = new Date();
      const targetDayOfWeek = new Date(dateStr).getDay();

      // Filter by date: prefer b.booking_date, then gc.date_str, fallback to day_of_week
      const filtered = data.filter((b: any) => {
        const gc = b.group_class;
        if (!gc) return false;
        if (b.booking_date) return b.booking_date === dateStr;
        if (gc.date_str) return gc.date_str === dateStr;
        return (
          gc.day_of_week === undefined ||
          gc.day_of_week === null ||
          gc.day_of_week === targetDayOfWeek
        );
      });

      const seenKeys = new Set<string>();
      const result: BookedClassItem[] = [];

      for (const b of filtered) {
        const gc = b.group_class;
        const classId = b.class_id || gc?.id;
        const dedupKey = classId || `${gc?.name}_${gc?.start_time}_${gc?.end_time}`;
        
        if (seenKeys.has(dedupKey)) {
          continue;
        }
        seenKeys.add(dedupKey);

        const statusStr = (b.status || '').toLowerCase();
        const isWaitlist = statusStr === 'waitlist' || statusStr === 'waiting';

        // Calculate whether the class end time has passed
        const endTimeStr = gc?.end_time || gc?.start_time || '23:59';
        const [endH, endM] = endTimeStr.split(':');
        const classEndTime = new Date(
          `${dateStr}T${String(endH).padStart(2, '0')}:${String(endM).padStart(2, '0')}:00`
        );
        const isPast = classEndTime < now;

        result.push({
          bookingId: b.id,
          classId: classId,
          name: gc?.name || 'Grup Dersi',
          instructorName: gc?.instructor_name,
          startTime: gc?.start_time ? gc.start_time.substring(0, 5) : undefined,
          endTime: gc?.end_time ? gc.end_time.substring(0, 5) : undefined,
          status: b.status || 'confirmed',
          isPast,
          isWaitlist,
        });
      }

      return result;
    } catch (e) {
      console.error('Error fetching booked classes for date:', e);
      return [];
    }
  },
};

