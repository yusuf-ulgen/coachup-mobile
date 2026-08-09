import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  FlatList,
  ActivityIndicator,
  TextInput,

} from 'react-native';
import {
  ChevronLeft,
  QrCode,
  Keyboard,
  List,
  Search,
  X,
  Calendar,
  Clock,
  BarChart2,
  AlertTriangle,
  Inbox,
} from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { QRService, EntryHistory } from '../../services/qrService';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

type FilterType = 'all' | 'qr' | 'manual';

function formatEntryDate(date: Date): string {
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);

  if (
    date.getFullYear() === today.getFullYear() &&
    date.getMonth() === today.getMonth() &&
    date.getDate() === today.getDate()
  ) {
    return 'Bugün';
  } else if (
    date.getFullYear() === yesterday.getFullYear() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getDate() === yesterday.getDate()
  ) {
    return 'Dün';
  } else {
    return date.toLocaleDateString('tr-TR', { day: 'numeric', month: 'long' });
  }
}

interface AllEntryHistoryScreenProps {
  navigation: any;
}

export const AllEntryHistoryScreen: React.FC<AllEntryHistoryScreenProps> = ({ navigation }) => {
  const insets = useSafeAreaInsets();
  const [entries, setEntries] = useState<EntryHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedFilter, setSelectedFilter] = useState<FilterType>('all');
  const [searchText, setSearchText] = useState('');

  const loadEntries = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const user = await AuthService.getCurrentUser();
      if (!user) return;
      const data = await QRService.fetchEntries(user.id, 100);
      setEntries(data);
    } catch (e: any) {
      setError(e?.message || 'Yüklenemedi');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadEntries();
  }, [loadEntries]);

  const filtered = entries
    .filter((e) => {
      if (selectedFilter === 'qr') return e.type === 'qr';
      if (selectedFilter === 'manual') return e.type === 'manual';
      return true;
    })
    .filter((e) => {
      if (!searchText.trim()) return true;
      return (
        e.location.toLowerCase().includes(searchText.toLowerCase()) ||
        e.time.includes(searchText)
      );
    });

  const thisMonthCount = entries.filter((e) => {
    const now = new Date();
    return e.date.getFullYear() === now.getFullYear() && e.date.getMonth() === now.getMonth();
  }).length;
  const qrCount = entries.filter((e) => e.type === 'qr').length;
  const manualCount = entries.filter((e) => e.type === 'manual').length;

  const filters: { key: FilterType; label: string }[] = [
    { key: 'all', label: 'Tümü' },
    { key: 'qr', label: 'QR' },
    { key: 'manual', label: 'Manuel' },
  ];

  const renderItem = ({ item, index }: { item: EntryHistory; index: number }) => (
    <View>
      <View style={styles.historyRow}>
        <View style={styles.iconBox}>
          {item.type === 'qr' ? (
            <QrCode size={20} color={Colors.primary} />
          ) : (
            <Keyboard size={20} color={Colors.primary} />
          )}
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.rowLocation}>{item.location}</Text>
          <Text style={styles.rowDate}>{formatEntryDate(item.date)}</Text>
        </View>
        <Text style={styles.rowTime}>{item.time}</Text>
      </View>
      {index < filtered.length - 1 && <View style={styles.divider} />}
    </View>
  );

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity style={styles.backBtn} onPress={() => navigation.goBack()} activeOpacity={0.7}>
          <ChevronLeft size={18} color={Colors.textDark} />
        </TouchableOpacity>
        <View style={{ flex: 1, paddingLeft: 8 }}>
          <Text style={styles.headerTitle}>Giriş Geçmişi</Text>
          <Text style={styles.headerCount}>{filtered.length} kayıt</Text>
        </View>
      </View>

      {/* Filter Chips */}
      <View style={styles.filterRow}>
        {filters.map((f) => {
          const isSelected = selectedFilter === f.key;
          return (
            <TouchableOpacity
              key={f.key}
              style={[styles.filterChip, isSelected && styles.filterChipSelected]}
              onPress={() => setSelectedFilter(f.key)}
              activeOpacity={0.8}
            >
              {f.key === 'all' && <List size={14} color={isSelected ? Colors.allWhite : Colors.textDark} />}
              {f.key === 'qr' && <QrCode size={14} color={isSelected ? Colors.allWhite : Colors.textDark} />}
              {f.key === 'manual' && <Keyboard size={14} color={isSelected ? Colors.allWhite : Colors.textDark} />}
              <Text style={[styles.filterChipText, isSelected && styles.filterChipTextSelected]}>
                {f.label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {/* Search Bar */}
      <View style={styles.searchBar}>
        <Search size={16} color={Colors.textSecondaryDark} />
        <TextInput
          style={styles.searchInput}
          placeholder="Ara..."
          placeholderTextColor={Colors.textSecondaryDark}
          value={searchText}
          onChangeText={setSearchText}
        />
        {searchText.length > 0 && (
          <TouchableOpacity onPress={() => setSearchText('')}>
            <X size={16} color={Colors.textSecondaryDark} />
          </TouchableOpacity>
        )}
      </View>

      {/* Stats Summary */}
      <View style={styles.statsRow}>
        <View style={[styles.statCard, { backgroundColor: 'rgba(255,96,71,0.1)' }]}>
          <View style={styles.statCardHeader}>
            <Calendar size={12} color={Colors.primary} />
            <Text style={styles.statCardTitle}>Toplam Giriş</Text>
          </View>
          <Text style={styles.statCardValue}>{entries.length}</Text>
        </View>
        <View style={[styles.statCard, { backgroundColor: 'rgba(33,150,243,0.1)' }]}>
          <View style={styles.statCardHeader}>
            <Clock size={12} color={Colors.info} />
            <Text style={styles.statCardTitle}>Bu Ay</Text>
          </View>
          <Text style={styles.statCardValue}>{thisMonthCount}</Text>
        </View>
        <View style={[styles.statCard, { backgroundColor: 'rgba(76,175,80,0.1)' }]}>
          <View style={styles.statCardHeader}>
            <BarChart2 size={12} color={Colors.success} />
            <Text style={styles.statCardTitle}>QR / Manuel</Text>
          </View>
          <Text style={styles.statCardValue}>{qrCount}/{manualCount}</Text>
        </View>
      </View>

      {/* Content */}
      {loading ? (
        <View style={styles.centerBox}>
          <ActivityIndicator size="small" color={Colors.primary} />
        </View>
      ) : error ? (
        <View style={styles.centerBox}>
          <AlertTriangle size={36} color={Colors.warning} />
          <Text style={styles.errorText}>{error}</Text>
          <TouchableOpacity onPress={loadEntries}>
            <Text style={styles.retryText}>Tekrar Dene</Text>
          </TouchableOpacity>
        </View>
      ) : filtered.length === 0 ? (
        <View style={styles.centerBox}>
          <Inbox size={48} color={Colors.textSecondaryDark} />
          <Text style={styles.emptyTitle}>Kayıt Bulunamadı</Text>
          <Text style={styles.emptySubtitle}>
            {searchText ? 'Arama kriterlerine uygun kayıt bulunamadı' : 'Henüz giriş kaydı yok'}
          </Text>
        </View>
      ) : (
        <FlatList
          data={filtered}
          keyExtractor={(item) => item.id}
          renderItem={renderItem}
          contentContainerStyle={{ paddingBottom: 24 + insets.bottom }}
        />
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 12,
  },
  backBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: '700',
    color: Colors.textDark,
  },
  headerCount: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  filterRow: {
    flexDirection: 'row',
    paddingHorizontal: 20,
    paddingBottom: 16,
    gap: 8,
  },
  filterChip: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    paddingVertical: 12,
  },
  filterChipSelected: {
    backgroundColor: Colors.primary,
  },
  filterChipText: {
    fontSize: 14,
    fontWeight: '500',
    color: Colors.textDark,
  },
  filterChipTextSelected: {
    color: Colors.allWhite,
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginHorizontal: 20,
    marginBottom: 16,
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  searchInput: {
    flex: 1,
    color: Colors.textDark,
    fontSize: 15,
  },
  statsRow: {
    flexDirection: 'row',
    gap: 12,
    marginHorizontal: 20,
    marginBottom: 16,
  },
  statCard: {
    flex: 1,
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 8,
    alignItems: 'center',
    gap: 8,
  },
  statCardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  statCardTitle: {
    fontSize: 11,
    color: Colors.textSecondaryDark,
    textAlign: 'center',
  },
  statCardValue: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  centerBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
    gap: 12,
  },
  errorText: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    textAlign: 'center',
  },
  retryText: {
    color: Colors.primary,
    fontSize: 14,
    fontWeight: '600',
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: Colors.textDark,
  },
  emptySubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    textAlign: 'center',
  },
  historyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 12,
    gap: 12,
  },
  iconBox: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(255,96,71,0.1)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  rowLocation: {
    fontSize: 15,
    fontWeight: '500',
    color: Colors.textDark,
  },
  rowDate: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
  },
  rowTime: {
    fontSize: 14,
    fontWeight: '500',
    color: Colors.textDark,
  },
  divider: {
    height: 1,
    backgroundColor: Colors.borderDark,
    marginLeft: 20 + 44 + 12,
    opacity: 0.5,
  },
});
