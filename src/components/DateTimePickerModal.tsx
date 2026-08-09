import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
} from 'react-native';
import { Calendar as CalendarIcon, Clock, ChevronLeft, ChevronRight, X, Check } from 'lucide-react-native';
import { Colors } from '../theme/colors';
import { SmoothModal } from './motion/SmoothModal';

interface DateTimePickerModalProps {
  visible: boolean;
  mode: 'date' | 'time';
  title?: string;
  initialValue?: string;
  onConfirm: (val: string) => void;
  onCancel: () => void;
}

const MONTH_NAMES = [
  'Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran',
  'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık'
];
const DAY_LABELS = ['Pz', 'Pt', 'Sa', 'Çş', 'Pş', 'Cu', 'Ct'];

const DEFAULT_TIME_SLOTS = [
  '08:00 - 09:00',
  '09:00 - 10:00',
  '10:00 - 11:00',
  '11:00 - 12:00',
  '12:00 - 13:00',
  '13:00 - 14:00',
  '14:00 - 15:00',
  '15:00 - 16:00',
  '16:00 - 17:00',
  '17:00 - 18:00',
  '18:00 - 19:00',
  '19:00 - 20:00',
  '20:00 - 21:00',
];

export const DateTimePickerModal: React.FC<DateTimePickerModalProps> = ({
  visible,
  mode,
  title,
  initialValue,
  onConfirm,
  onCancel,
}) => {
  // Calendar date state
  const [currentViewDate, setCurrentViewDate] = useState(new Date());
  const [selectedDateStr, setSelectedDateStr] = useState('');

  // Time state
  const [selectedTimeSlot, setSelectedTimeSlot] = useState('');

  useEffect(() => {
    if (visible) {
      if (mode === 'date') {
        if (initialValue && initialValue.includes('-')) {
          const parts = initialValue.split('-');
          if (parts.length === 3) {
            const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
            if (!isNaN(d.getTime())) {
              setCurrentViewDate(d);
              setSelectedDateStr(initialValue);
              return;
            }
          }
        }
        const today = new Date();
        setCurrentViewDate(today);
        setSelectedDateStr(today.toISOString().split('T')[0]);
      } else {
        setSelectedTimeSlot(initialValue || DEFAULT_TIME_SLOTS[2]);
      }
    }
  }, [visible, mode, initialValue]);

  const year = currentViewDate.getFullYear();
  const month = currentViewDate.getMonth();

  const handlePrevMonth = () => {
    setCurrentViewDate(new Date(year, month - 1, 1));
  };

  const handleNextMonth = () => {
    setCurrentViewDate(new Date(year, month + 1, 1));
  };

  // Generate calendar days grid
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const firstDayOfWeek = new Date(year, month, 1).getDay(); // 0 is Sunday

  const calendarGrid: (number | null)[] = [];
  for (let i = 0; i < firstDayOfWeek; i++) {
    calendarGrid.push(null);
  }
  for (let day = 1; day <= daysInMonth; day++) {
    calendarGrid.push(day);
  }

  const handleSelectDay = (day: number) => {
    const formattedMonth = String(month + 1).padStart(2, '0');
    const formattedDay = String(day).padStart(2, '0');
    const fullStr = `${year}-${formattedMonth}-${formattedDay}`;
    setSelectedDateStr(fullStr);
  };

  const handleConfirmDate = () => {
    onConfirm(selectedDateStr);
  };

  const handleConfirmTime = (time: string) => {
    setSelectedTimeSlot(time);
    onConfirm(time);
  };

  return (
    <SmoothModal visible={visible} onClose={onCancel} variant="modal">
      <View style={styles.modalCard}>
        {/* Header */}
        <View style={styles.headerRow}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            {mode === 'date' ? (
              <CalendarIcon size={20} color={Colors.primary} />
            ) : (
              <Clock size={20} color={Colors.primary} />
            )}
            <Text style={styles.headerTitle}>
              {title || (mode === 'date' ? 'Tarih Seç' : 'Saat Seç')}
            </Text>
          </View>
          <TouchableOpacity onPress={onCancel} style={styles.closeBtn}>
            <X size={20} color={Colors.textDark} />
          </TouchableOpacity>
        </View>

        {mode === 'date' ? (
          /* DATE MODE: Calendar Grid */
          <View style={styles.calendarContainer}>
            {/* Month Navigation */}
            <View style={styles.monthHeader}>
              <TouchableOpacity onPress={handlePrevMonth} style={styles.arrowBtn}>
                <ChevronLeft size={20} color={Colors.textDark} />
              </TouchableOpacity>
              <Text style={styles.monthTitle}>
                {MONTH_NAMES[month]} {year}
              </Text>
              <TouchableOpacity onPress={handleNextMonth} style={styles.arrowBtn}>
                <ChevronRight size={20} color={Colors.textDark} />
              </TouchableOpacity>
            </View>

            {/* Day Name Headers */}
            <View style={styles.dayLabelsRow}>
              {DAY_LABELS.map((label) => (
                <Text key={label} style={styles.dayLabel}>
                  {label}
                </Text>
              ))}
            </View>

            {/* Days Grid */}
            <View style={styles.grid}>
              {calendarGrid.map((dayNum, idx) => {
                if (dayNum === null) {
                  return <View key={`empty_${idx}`} style={styles.dayCell} />;
                }
                const formattedMonth = String(month + 1).padStart(2, '0');
                const formattedDay = String(dayNum).padStart(2, '0');
                const cellDateStr = `${year}-${formattedMonth}-${formattedDay}`;
                const isSelected = cellDateStr === selectedDateStr;

                return (
                  <TouchableOpacity
                    key={`day_${dayNum}`}
                    style={[styles.dayCell, isSelected && styles.selectedDayCell]}
                    onPress={() => handleSelectDay(dayNum)}
                  >
                    <Text
                      style={[
                        styles.dayCellText,
                        isSelected && styles.selectedDayCellText,
                      ]}
                    >
                      {dayNum}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>

            {/* Confirm Action */}
            <TouchableOpacity style={styles.confirmBtn} onPress={handleConfirmDate}>
              <Check size={18} color="#fff" style={{ marginRight: 6 }} />
              <Text style={styles.confirmBtnText}>Tarihi Seç ({selectedDateStr})</Text>
            </TouchableOpacity>
          </View>
        ) : (
          /* TIME MODE: Time Slots */
          <View style={{ maxHeight: 340 }}>
            <ScrollView contentContainerStyle={styles.timeSlotsContainer}>
              {DEFAULT_TIME_SLOTS.map((slot) => {
                const isSelected = slot === selectedTimeSlot;
                return (
                  <TouchableOpacity
                    key={slot}
                    style={[
                      styles.timeSlotChip,
                      isSelected && styles.selectedTimeSlotChip,
                    ]}
                    onPress={() => handleConfirmTime(slot)}
                  >
                    <Clock
                      size={16}
                      color={isSelected ? '#fff' : Colors.textSecondaryDark}
                    />
                    <Text
                      style={[
                        styles.timeSlotText,
                        isSelected && styles.selectedTimeSlotText,
                      ]}
                    >
                      {slot}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </ScrollView>
          </View>
        )}
      </View>
    </SmoothModal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  modalCard: {
    width: '100%',
    maxWidth: 360,
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderDark,
  },
  headerTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: Colors.textDark,
  },
  closeBtn: {
    padding: 4,
  },
  calendarContainer: {
    alignItems: 'center',
  },
  monthHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    width: '100%',
    marginBottom: 12,
  },
  arrowBtn: {
    padding: 8,
  },
  monthTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.textDark,
  },
  dayLabelsRow: {
    flexDirection: 'row',
    width: '100%',
    justifyContent: 'space-around',
    marginBottom: 8,
  },
  dayLabel: {
    width: 38,
    textAlign: 'center',
    fontSize: 13,
    fontWeight: '600',
    color: Colors.textSecondaryDark,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    width: '100%',
    justifyContent: 'flex-start',
  },
  dayCell: {
    width: '14.28%',
    height: 38,
    justifyContent: 'center',
    alignItems: 'center',
    marginVertical: 2,
    borderRadius: 8,
  },
  selectedDayCell: {
    backgroundColor: Colors.primary,
  },
  dayCellText: {
    fontSize: 14,
    color: Colors.textDark,
    fontWeight: '500',
  },
  selectedDayCellText: {
    color: '#fff',
    fontWeight: '700',
  },
  confirmBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Colors.primary,
    borderRadius: 12,
    paddingVertical: 12,
    width: '100%',
    marginTop: 16,
  },
  confirmBtnText: {
    color: '#fff',
    fontSize: 15,
    fontWeight: '600',
  },
  timeSlotsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    justifyContent: 'space-between',
    paddingVertical: 8,
  },
  timeSlotChip: {
    width: '48%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingVertical: 12,
    paddingHorizontal: 12,
    borderRadius: 12,
    backgroundColor: Colors.backgroundDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  selectedTimeSlotChip: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  timeSlotText: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.textDark,
  },
  selectedTimeSlotText: {
    color: '#fff',
  },
});
