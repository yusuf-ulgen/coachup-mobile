import { supabase } from './supabaseClient';

export interface EntryHistory {
  id: string;
  type: 'qr' | 'manual';
  entry_type: 'entry' | 'exit';
  location: string;
  date: Date;
  time: string;
  entry_time: string;
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
  // 6-digit TOTP: try to guess from raw value (default entry)
  return 'entry';
}

export const QRService = {
  async fetchEntryCount(userId: string): Promise<number> {
    const { count, error } = await supabase
      .from('gym_entries')
      .select('*', { count: 'exact', head: true })
      .eq('user_id', userId);
    return count || 0;
  },

  async fetchEntries(userId: string, limit = 50): Promise<EntryHistory[]> {
    const { data, error } = await supabase
      .from('gym_entries')
      .select('*')
      .eq('user_id', userId)
      .order('entry_time', { ascending: false })
      .limit(limit);

    if (error || !data) return [];

    return data.map((row: any) => {
      const dt = new Date(row.entry_time);
      const timeStr = dt.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
      const location = row.entry_type === 'exit' ? 'Salon Çıkışı' : 'Salon Girişi';
      return {
        id: row.id,
        type: (row.entry_method === 'manual' ? 'manual' : 'qr') as 'qr' | 'manual',
        entry_type: row.entry_type as 'entry' | 'exit',
        location,
        date: dt,
        time: timeStr,
        entry_time: row.entry_time,
      };
    });
  },

  async recordEntry(
    userId: string,
    code: string,
    method: 'qr' | 'manual'
  ): Promise<void> {
    const entryType = resolveCodeType(code);
    
    if (entryType === 'entry') {
      const { error } = await supabase.functions.invoke('qr-validate', {
        body: { code, method }
      });
      if (error) throw new Error(error.message || 'Giriş işlemi başarısız');
    } else {
      const { error } = await supabase.functions.invoke('qr-exit', {
        body: { code, method }
      });
      if (error) throw new Error(error.message || 'Çıkış işlemi başarısız');
    }
  },
};
