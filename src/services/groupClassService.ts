import { supabase } from './supabaseClient';
import { formatLocalDate } from '../utils/dateUtils';
import { UserService } from './userService';

export type CanonicalClassBookingStatus = 'booked' | 'waiting' | 'cancelled' | 'other';

export function normalizeClassBookingStatus(status?: string | null): CanonicalClassBookingStatus {
  if (!status) return 'other';
  const s = status.toLowerCase().trim();
  if (s === 'booked' || s === 'confirmed') return 'booked';
  if (s === 'waiting' || s === 'waitlist') return 'waiting';
  if (s === 'cancelled' || s === 'canceled') return 'cancelled';
  return 'other';
}

export function getDayOfWeekFromDateStr(dateStr: string): number {
  const parts = dateStr.split('-');
  if (parts.length === 3) {
    const year = parseInt(parts[0], 10);
    const month = parseInt(parts[1], 10) - 1;
    const day = parseInt(parts[2], 10);
    const d = new Date(year, month, day);
    return d.getDay();
  }
  return new Date(dateStr).getDay();
}

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
  enrolled_count?: number;
  current_participants?: number;
}

export interface ClassBooking {
  id: string;
  class_id: string;
  user_id: string;
  created_at?: string;
  status?: string;
  booking_date?: string;
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
  async fetchBookingsForDate(userId: string, dateStr: string): Promise<ClassBooking[]> {
    try {
      const { data: userProfile } = await supabase
        .from('users')
        .select('*')
        .eq('id', userId)
        .single();

      const activeGymId = userProfile
        ? await UserService.resolveActiveGymIdForContent(userProfile)
        : null;

      if (!activeGymId || activeGymId === 'staff') return [];

      const { data, error } = await supabase
        .from('class_bookings')
        .select('*, group_class:group_classes(*)')
        .eq('user_id', userId)
        .or(`booking_date.eq.${dateStr},booking_date.is.null`)
        .neq('status', 'cancelled');

      if (error) {
        console.error('[GroupClassService] fetchBookingsForDate error:', error);
        throw error;
      }

      const filtered = (data || []).filter((b: any) => {
        const gc = b.group_class;
        if (!gc) return false;
        if (gc.gym_id && gc.gym_id !== activeGymId) return false;
        if (b.booking_date) {
          return b.booking_date === dateStr;
        }
        return Boolean(gc.date_str && gc.date_str === dateStr);
      });

      return filtered.map((b: any) => {
        const canonicalStatus = normalizeClassBookingStatus(b.status);
        return {
          ...b,
          status: canonicalStatus,
          is_waitlist: canonicalStatus === 'waiting',
        };
      });
    } catch (e) {
      console.error('[GroupClassService] Error fetching class bookings:', e);
      return [];
    }
  },

  async fetchAvailableGroupClasses(gymId: string): Promise<GroupClass[]> {
    try {
      const { data, error } = await supabase
        .from('group_classes')
        .select('*')
        .eq('gym_id', gymId)
        .eq('is_active', true)
        .order('start_time', { ascending: true });

      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('[GroupClassService] Error fetching available group classes:', e);
      return [];
    }
  },

  async fetchClassesForDate(userId: string, dateStr: string): Promise<GroupClass[]> {
    try {
      const { data: userProfile } = await supabase
        .from('users')
        .select('*')
        .eq('id', userId)
        .single();

      if (!userProfile) return [];

      const activeGymId = await UserService.resolveActiveGymIdForContent(userProfile);
      if (!activeGymId || activeGymId === 'staff') return [];

      const { data, error } = await supabase
        .from('group_classes')
        .select('*')
        .eq('gym_id', activeGymId)
        .eq('is_active', true)
        .order('start_time', { ascending: true });

      if (error) {
        console.error('[GroupClassService] fetchClassesForDate error:', error);
        throw error;
      }
      if (!data || data.length === 0) return [];

      const targetDay = getDayOfWeekFromDateStr(dateStr);
      const filtered = data.filter((c: any) => {
        if (c.date_str) {
          return c.date_str === dateStr;
        }
        if (c.day_of_week !== undefined && c.day_of_week !== null) {
          return c.day_of_week === targetDay;
        }
        return false;
      });

      if (filtered.length === 0) return [];

      // Calculate dynamic participant counts per occurrence date
      const classIds = filtered.map((c: any) => c.id);
      const { data: bookingsData, error: bErr } = await supabase
        .from('class_bookings')
        .select('class_id, status, booking_date')
        .in('class_id', classIds)
        .in('status', ['booked', 'confirmed'])
        .eq('booking_date', dateStr);

      if (bErr) {
        console.warn('[GroupClassService] fetchClassesForDate bookings count warning:', bErr);
      }

      const countMap: Record<string, number> = {};
      (bookingsData || []).forEach((b: any) => {
        countMap[b.class_id] = (countMap[b.class_id] || 0) + 1;
      });

      return filtered.map((c: any) => ({
        ...c,
        enrolled_count: countMap[c.id] || 0,
        current_participants: countMap[c.id] || 0,
      }));
    } catch (e) {
      console.error('[GroupClassService] Error fetching classes for date:', e);
      return [];
    }
  },

  async bookClass(userId: string, classId: string, bookingDate: string) {
    if (!bookingDate) {
      throw new Error('Rezervasyon tarihi zorunludur.');
    }

    try {
      const { data, error } = await supabase.rpc('atomic_book_group_class_v2', {
        p_user_id: userId,
        p_class_id: classId,
        p_booking_date: bookingDate,
      });

      if (error) {
        console.error('[GroupClassService] atomic_book_group_class_v2 RPC error:', error);
        throw new Error(error.message || 'Ders rezervasyonu gerçekleştirilemedi.');
      }

      if (data && data.success === false) {
        throw new Error(data.error || 'Ders rezervasyonu gerçekleştirilemedi.');
      }

      const normalizedStatus = normalizeClassBookingStatus(data?.status);
      return {
        ...data,
        status: normalizedStatus,
        is_waiting: normalizedStatus === 'waiting',
      };
    } catch (err: any) {
      console.error('[GroupClassService] bookClass failed:', err);
      throw err;
    }
  },

  async cancelBooking(bookingId: string, userId?: string) {
    const currentUserId = userId || (await supabase.auth.getUser()).data.user?.id;
    if (!currentUserId) {
      throw new Error('Kullanıcı oturumu bulunamadı.');
    }

    try {
      const { data, error } = await supabase.rpc('atomic_cancel_group_class_v2', {
        p_user_id: currentUserId,
        p_booking_id: bookingId,
      });

      if (error) {
        console.error('[GroupClassService] atomic_cancel_group_class_v2 RPC error:', error);
        throw new Error(error.message || 'Rezervasyon iptal edilemedi.');
      }

      if (data && data.success === false) {
        throw new Error(data.error || 'Rezervasyon iptal edilemedi.');
      }

      return data;
    } catch (err: any) {
      console.error('[GroupClassService] cancelBooking failed:', err);
      throw err;
    }
  },

  // Fetch only the classes the user has booked for a specific date,
  // enriched with isPast and isWaitlist status.
  async fetchBookedClassesForDate(userId: string, dateStr: string): Promise<BookedClassItem[]> {
    try {
      const { data: userProfile } = await supabase
        .from('users')
        .select('*')
        .eq('id', userId)
        .single();

      const activeGymId = userProfile
        ? await UserService.resolveActiveGymIdForContent(userProfile)
        : null;

      if (!activeGymId || activeGymId === 'staff') return [];

      const { data, error } = await supabase
        .from('class_bookings')
        .select('*, group_class:group_classes(*)')
        .eq('user_id', userId)
        .or(`booking_date.eq.${dateStr},booking_date.is.null`)
        .neq('status', 'cancelled')
        .order('created_at', { ascending: false });

      if (error) {
        console.error('[GroupClassService] fetchBookedClassesForDate error:', error);
        throw error;
      }
      if (!data) return [];

      const now = new Date();

      const filtered = data.filter((b: any) => {
        const gc = b.group_class;
        if (!gc) return false;
        if (gc.gym_id && gc.gym_id !== activeGymId) return false;
        if (b.booking_date) {
          return b.booking_date === dateStr;
        }
        // Legacy undated row: only include if group_class is one-off with matching date_str
        return Boolean(gc.date_str && gc.date_str === dateStr);
      });

      const seenKeys = new Set<string>();
      const result: BookedClassItem[] = [];

      for (const b of filtered) {
        const gc = b.group_class;
        const classId = b.class_id || gc?.id;
        const dedupKey = `${classId}_${dateStr}`;

        if (seenKeys.has(dedupKey)) {
          continue;
        }
        seenKeys.add(dedupKey);

        const canonicalStatus = normalizeClassBookingStatus(b.status);
        // Neutral 'other' or 'cancelled' states should not be treated as active/booked
        if (canonicalStatus !== 'booked' && canonicalStatus !== 'waiting') {
          continue;
        }

        const isWaitlist = canonicalStatus === 'waiting';

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
          status: canonicalStatus,
          isPast,
          isWaitlist,
        });
      }

      return result;
    } catch (e) {
      console.error('[GroupClassService] Error fetching booked classes for date:', e);
      return [];
    }
  },
};

