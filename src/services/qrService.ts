import { supabase } from './supabaseClient';

export interface EntryHistory {
  id: string;
  type: 'qr' | 'manual';
  entry_type: 'entry' | 'exit';
  location: string;
  date: Date;
  time: string;
  entry_time: string;
  exit_time?: string | null;
}

// Validates a QR / manual code. Returns an error string or null if valid.
export function validateCode(code: string): string | null {
  if (!code || !code.trim()) return 'Kod boş olamaz';
  const trimmed = code.trim();
  // 6-digit TOTP
  if (/^\d{6}$/.test(trimmed)) return null;
  // COACHUP prefixed codes
  if (trimmed.startsWith('COACHUP_ENTRY:') || trimmed.startsWith('COACHUP_EXIT:')) return null;
  return 'Geçersiz kod formatı';
}

export function resolveCodeType(code: string): 'entry' | 'exit' {
  if (code.startsWith('COACHUP_EXIT:')) return 'exit';
  if (code.startsWith('COACHUP_ENTRY:')) return 'entry';
  // 6-digit TOTP: default entry
  return 'entry';
}

export const QRService = {
  async fetchEntryCount(userId: string): Promise<number> {
    const { count, error } = await supabase
      .from('qr_entries')
      .select('*', { count: 'exact', head: true })
      .eq('user_id', userId);
    return count || 0;
  },

  async fetchEntries(userId: string, limit = 50): Promise<EntryHistory[]> {
    const { data, error } = await supabase
      .from('qr_entries')
      .select('*')
      .eq('user_id', userId)
      .order('entry_timestamp', { ascending: false })
      .limit(limit);

    if (error || !data) return [];

    return data.map((row: any) => {
      const timestampStr = row.entry_timestamp || (row.entry_date && row.entry_time ? `${row.entry_date}T${row.entry_time}` : row.created_at);
      const dt = timestampStr ? new Date(timestampStr) : new Date();
      const timeStr = dt.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
      const isExit = !!row.exit_time;
      const location = isExit ? 'Salon Çıkışı' : 'Salon Girişi';

      return {
        id: row.id,
        type: (row.entry_method === 'manual' ? 'manual' : 'qr') as 'qr' | 'manual',
        entry_type: isExit ? 'exit' : 'entry',
        location,
        date: dt,
        time: timeStr,
        entry_time: timestampStr,
        exit_time: row.exit_time,
      };
    });
  },

  async recordEntry(
    userId: string,
    code: string,
    _method: 'qr' | 'manual'
  ): Promise<void> {
    const entryType = resolveCodeType(code);
    
    if (entryType === 'entry') {
      const { data, error } = await supabase.functions.invoke('qr-validate', {
        body: { qr: code, user_id: userId }
      });
      if (error || data?.error) {
        throw new Error(data?.error || error?.message || 'Giriş işlemi başarısız');
      }
    } else {
      const { data, error } = await supabase.functions.invoke('qr-exit', {
        body: { qr: code, user_id: userId }
      });
      if (error || data?.error) {
        throw new Error(data?.error || error?.message || 'Çıkış işlemi başarısız');
      }
    }
  },
};
