import 'react-native-url-polyfill/auto';
import 'react-native-get-random-values';
import { createClient } from '@supabase/supabase-js';
import AsyncStorage from '@react-native-async-storage/async-storage';

export const SUPABASE_URL =
  process.env.EXPO_PUBLIC_SUPABASE_URL ||
  'https://auiebboyocmkkxbdahqf.supabase.co';

export const SUPABASE_KEY =
  process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY ||
  process.env.EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY ||
  'sb_publishable_gldj0fxGYVdS5WmeTEQC0Q_L5sPqgEa';

export const supabase = createClient(SUPABASE_URL, SUPABASE_KEY, {
  auth: {
    storage: AsyncStorage,
    autoRefreshToken: true,
    persistSession: true,
    detectSessionInUrl: false,
  },
});
