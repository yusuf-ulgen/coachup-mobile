import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  TextInput,
  ActivityIndicator,
  Modal,
  Animated,
  Easing,
} from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import {
  Keyboard,
  CheckCircle,
  QrCode,
  Camera as CameraIcon,
  ChevronRight,
  TriangleAlert,
  CircleAlert,
} from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { Header } from '../../components/Header';
import { SideMenu } from '../../components/SideMenu';
import { AuthService } from '../../services/authService';
import { QRService, EntryHistory, validateCode, resolveCodeType } from '../../services/qrService';
import { feedback } from '../../services/feedbackService';

import { useSafeAreaInsets } from 'react-native-safe-area-context';

interface QREntryScreenProps {
  navigation?: any;
}

// ── Helpers ─────────────────────────────────────────────────────────────────

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

// ── Scan Line Animation Component ────────────────────────────────────────────

const ScanLineOverlay: React.FC = () => {
  const scanAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(scanAnim, {
          toValue: 1,
          duration: 2000,
          easing: Easing.linear,
          useNativeDriver: true,
        }),
        Animated.timing(scanAnim, {
          toValue: 0,
          duration: 2000,
          easing: Easing.linear,
          useNativeDriver: true,
        }),
      ])
    );
    loop.start();
    return () => loop.stop();
  }, [scanAnim]);

  const translateY = scanAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [-80, 80],
  });

  return (
    <View style={styles.qrFrameOverlay} pointerEvents="none">
      {/* Corner markers */}
      <View style={[styles.corner, styles.cornerTL]} />
      <View style={[styles.corner, styles.cornerTR]} />
      <View style={[styles.corner, styles.cornerBL]} />
      <View style={[styles.corner, styles.cornerBR]} />
      {/* Scan line */}
      <Animated.View
        style={[styles.scanLine, { transform: [{ translateY }] }]}
      />
    </View>
  );
};

// ── Main Screen ──────────────────────────────────────────────────────────────

export const QREntryScreen: React.FC<QREntryScreenProps> = ({ navigation }) => {
  const insets = useSafeAreaInsets();
  const [menuVisible, setMenuVisible] = useState(false);
  const [permission, requestPermission] = useCameraPermissions();

  const [scanned, setScanned] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [isScanning, setIsScanning] = useState(true);

  const [showSuccess, setShowSuccess] = useState(false);
  const [successType, setSuccessType] = useState<'entry' | 'exit'>('entry');

  const [showManualEntry, setShowManualEntry] = useState(false);
  const [manualCode, setManualCode] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);

  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [entries, setEntries] = useState<EntryHistory[]>([]);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);

  // ── Data loading ─────────────────────────────────────────────────────────

  const loadHistory = useCallback(async () => {
    setIsLoadingHistory(true);
    setHistoryError(null);
    try {
      const user = await AuthService.getCurrentUser();
      if (!user) return;
      const data = await QRService.fetchEntries(user.id, 5);
      setEntries(data);
    } catch (e: any) {
      setHistoryError(e?.message || 'Geçmiş yüklenemedi');
    } finally {
      setIsLoadingHistory(false);
    }
  }, []);

  useEffect(() => {
    if (!permission?.granted) requestPermission();
    loadHistory();
  }, []);

  // ── Handlers ─────────────────────────────────────────────────────────────

  const handleScannedCode = useCallback(
    async (code: string) => {
      const scanError = validateCode(code);
      if (scanError) {
        feedback.error({ title: 'Hata', message: scanError });
        setScanned(false);
        setIsScanning(true);
        return;
      }

      setIsScanning(false);
      setIsProcessing(true);

      try {
        const user = await AuthService.getCurrentUser();
        if (!user) throw new Error('Kullanıcı bulunamadı');

        const type = resolveCodeType(code);
        setSuccessType(type);

        await QRService.recordEntry(user.id, code, 'qr');

        setShowSuccess(true);
        loadHistory();

        setTimeout(() => {
          setShowSuccess(false);
          setScanned(false);
          setIsScanning(true);
        }, 2000);
      } catch (e: any) {
        feedback.error({ title: 'Hata', message: e, fallbackMessage: 'İşlem gerçekleştirilemedi.' });
        setScanned(false);
        setIsScanning(true);
      } finally {
        setIsProcessing(false);
      }
    },
    [loadHistory]
  );

  const handleManualCodeChange = (code: string) => {
    setManualCode(code);
    setValidationError(validateCode(code));
  };

  const handleManualSubmit = async () => {
    const err = validateCode(manualCode);
    if (err) {
      setValidationError(err);
      return;
    }

    setIsProcessing(true);
    try {
      const user = await AuthService.getCurrentUser();
      if (!user) throw new Error('Kullanıcı bulunamadı');

      const type = resolveCodeType(manualCode);
      setSuccessType(type);

      await QRService.recordEntry(user.id, manualCode, 'manual');

      setShowManualEntry(false);
      setManualCode('');
      setValidationError(null);
      setShowSuccess(true);
      loadHistory();

      setTimeout(() => {
        setShowSuccess(false);
        setIsScanning(true);
      }, 2000);
    } catch (e: any) {
      setErrorMessage(e?.message || 'İşlem gerçekleştirilemedi.');
    } finally {
      setIsProcessing(false);
    }
  };

  const openManualEntry = () => {
    setShowManualEntry(true);
    setIsScanning(false);
  };

  const closeManualEntry = () => {
    setShowManualEntry(false);
    setManualCode('');
    setValidationError(null);
    setIsScanning(true);
  };

  const canSubmitManual =
    manualCode.trim().length > 0 && !isProcessing && validationError === null;

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <View style={styles.container}>
      <Header navigation={navigation} showMenuButton={true} onMenuPress={() => setMenuVisible(true)} />
      <SideMenu visible={menuVisible} onClose={() => setMenuVisible(false)} navigation={navigation} />

      <ScrollView
        contentContainerStyle={[styles.scrollContent, { paddingBottom: 100 + insets.bottom }]}
        showsVerticalScrollIndicator={false}
      >
        {/* Page Header */}
        <View style={styles.pageHeader}>
          <Text style={styles.pageTitle}>Giriş / Çıkış</Text>
          <Text style={styles.pageSubtitle}>
            Giriş veya çıkış yapmak için QR kodu okutun ya da şifre girin
          </Text>
        </View>

        {/* Main Content */}
        {showSuccess ? (
          // ── Success View ─────────────────────────────────────────────────
          <View style={styles.successContainer}>
            <View style={styles.successIconBox}>
              <CheckCircle size={60} color={Colors.success} />
            </View>
            <Text style={styles.successTitle}>
              {successType === 'exit' ? 'Çıkış Başarılı!' : 'Giriş Başarılı!'}
            </Text>
            <Text style={styles.successSubtitle}>
              {successType === 'exit'
                ? 'Tekrar görüşmek üzere!'
                : 'Hoş geldiniz, iyi antrenmanlar!'}
            </Text>
          </View>
        ) : showManualEntry ? (
          // ── Manual Entry ─────────────────────────────────────────────────
          <View style={styles.manualCard}>
            <Text style={styles.manualLabel}>Giriş Kodu</Text>
            <TextInput
              style={[
                styles.manualInput,
                validationError ? styles.manualInputError : null,
              ]}
              placeholder="Kodu girin..."
              placeholderTextColor={Colors.textSecondaryDark}
              value={manualCode}
              onChangeText={handleManualCodeChange}
              textAlign="center"
            />
            {validationError ? (
              <View style={styles.validationRow}>
                <CircleAlert size={12} color={Colors.error} />
                <Text style={styles.validationText}>{validationError}</Text>
              </View>
            ) : null}
            <View style={styles.manualActions}>
              <TouchableOpacity style={styles.cancelButton} onPress={closeManualEntry}>
                <Text style={styles.cancelButtonText}>İptal</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.submitButton, !canSubmitManual && { opacity: 0.6 }]}
                onPress={handleManualSubmit}
                disabled={!canSubmitManual}
              >
                {isProcessing ? (
                  <ActivityIndicator size="small" color={Colors.allWhite} />
                ) : (
                  <Text style={styles.submitButtonText}>Giriş Yap</Text>
                )}
              </TouchableOpacity>
            </View>
          </View>
        ) : !permission?.granted ? (
          // ── Camera Permission ────────────────────────────────────────────
          <View style={styles.permissionCard}>
            <CameraIcon size={56} color={Colors.textSecondaryDark} />
            <Text style={styles.permissionTitle}>Kamera izni gerekli</Text>
            <Text style={styles.permissionSubtitle}>
              QR kod taramak için kamera erişimine izin verin
            </Text>
            <TouchableOpacity style={styles.permissionButton} onPress={requestPermission}>
              <Text style={styles.permissionButtonText}>İzin Ver</Text>
            </TouchableOpacity>
          </View>
        ) : (
          // ── QR Scanner ───────────────────────────────────────────────────
          <View style={styles.scannerSection}>
            <View style={styles.cameraBox}>
              <CameraView
                style={StyleSheet.absoluteFill}
                facing="back"
                barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
                onBarcodeScanned={
                  scanned || !isScanning
                    ? undefined
                    : ({ data }) => {
                        setScanned(true);
                        handleScannedCode(data);
                      }
                }
              />
              <ScanLineOverlay />
              {/* Processing overlay */}
              {isProcessing && (
                <View style={styles.processingOverlay}>
                  <ActivityIndicator size="large" color={Colors.allWhite} />
                  <Text style={styles.processingText}>İşleniyor...</Text>
                </View>
              )}
            </View>

            {/* Manual trigger button */}
            <TouchableOpacity
              style={styles.manualTrigger}
              onPress={openManualEntry}
              disabled={isProcessing}
              activeOpacity={0.8}
            >
              <Keyboard size={16} color={Colors.primary} />
              <Text style={styles.manualTriggerText}>Manuel Kod Gir</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* History Header */}
        <View style={styles.historyHeader}>
          <Text style={styles.historyTitle}>Giriş Geçmişi</Text>
          <TouchableOpacity
            onPress={() => navigation?.navigate('AllEntryHistory')}
            activeOpacity={0.7}
          >
            <Text style={styles.seeAllText}>Tümünü Gör</Text>
          </TouchableOpacity>
        </View>

        {/* History Content */}
        {isLoadingHistory ? (
          <ActivityIndicator
            size="small"
            color={Colors.primary}
            style={{ marginTop: 20 }}
          />
        ) : historyError ? (
          <View style={styles.historyErrorBox}>
            <TriangleAlert size={32} color={Colors.warning} />
            <Text style={styles.historyErrorText}>{historyError}</Text>
            <TouchableOpacity onPress={loadHistory}>
              <Text style={styles.retryText}>Tekrar Dene</Text>
            </TouchableOpacity>
          </View>
        ) : entries.length === 0 ? (
          <View style={styles.emptyHistoryCard}>
            <QrCode size={40} color={Colors.textSecondaryDark} />
            <Text style={styles.emptyHistoryText}>Henüz giriş kaydı yok</Text>
          </View>
        ) : (
          <View style={styles.historyList}>
            {entries.slice(0, 3).map((item, index) => (
              <View key={item.id}>
                <View style={styles.historyRow}>
                  <View style={styles.historyIconBox}>
                    {item.type === 'qr' ? (
                      <QrCode size={20} color={Colors.primary} />
                    ) : (
                      <Keyboard size={20} color={Colors.primary} />
                    )}
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.historyRowTitle}>{item.location}</Text>
                    <Text style={styles.historyRowDate}>{formatEntryDate(item.date)}</Text>
                  </View>
                  <Text style={styles.historyRowTime}>{item.time}</Text>
                </View>
                {index < Math.min(entries.length, 3) - 1 && (
                  <View style={styles.historyDivider} />
                )}
              </View>
            ))}

            {entries.length > 3 && (
              <TouchableOpacity
                style={styles.seeAllBanner}
                onPress={() => navigation?.navigate('AllEntryHistory')}
                activeOpacity={0.8}
              >
                <Text style={styles.seeAllBannerText}>
                  Tümünü Gör ({entries.length})
                </Text>
                <ChevronRight size={14} color={Colors.primary} />
              </TouchableOpacity>
            )}
          </View>
        )}
      </ScrollView>
    </View>
  );
};

const CORNER_SIZE = 20;
const CORNER_THICKNESS = 3;

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 100,
  },
  // ── Page header ──────────────────────────────────────────────────────────
  pageHeader: {
    marginBottom: 20,
  },
  pageTitle: {
    fontSize: 28,
    fontWeight: '700',
    color: Colors.textDark,
  },
  pageSubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 4,
  },
  // ── Success ──────────────────────────────────────────────────────────────
  successContainer: {
    height: 280,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 24,
  },
  successIconBox: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: 'rgba(76,175,80,0.15)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  successTitle: {
    fontSize: 24,
    fontWeight: '600',
    color: Colors.textDark,
    marginBottom: 8,
  },
  successSubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
  },
  // ── Manual entry ─────────────────────────────────────────────────────────
  manualCard: {
    marginBottom: 24,
    paddingVertical: 24,
  },
  manualLabel: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginBottom: 8,
  },
  manualInput: {
    backgroundColor: Colors.cardDark,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    color: Colors.textDark,
    fontSize: 18,
    paddingVertical: 14,
    paddingHorizontal: 16,
    marginBottom: 4,
  },
  manualInputError: {
    borderWidth: 2,
    borderColor: Colors.error,
  },
  validationRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginBottom: 12,
  },
  validationText: {
    fontSize: 12,
    color: Colors.error,
  },
  manualActions: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 20,
  },
  cancelButton: {
    flex: 1,
    borderRadius: 100,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    backgroundColor: Colors.cardDark,
    paddingVertical: 16,
    alignItems: 'center',
  },
  cancelButtonText: {
    color: Colors.textDark,
    fontWeight: '600',
  },
  submitButton: {
    flex: 1,
    borderRadius: 100,
    backgroundColor: Colors.primary,
    paddingVertical: 16,
    alignItems: 'center',
  },
  submitButtonText: {
    color: Colors.allWhite,
    fontWeight: '600',
  },
  // ── Camera permission ────────────────────────────────────────────────────
  permissionCard: {
    alignItems: 'center',
    justifyContent: 'center',
    height: 280,
    marginBottom: 24,
    gap: 12,
  },
  permissionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: Colors.textDark,
  },
  permissionSubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    textAlign: 'center',
  },
  permissionButton: {
    backgroundColor: Colors.primary,
    borderRadius: 100,
    paddingHorizontal: 32,
    paddingVertical: 14,
    marginTop: 8,
  },
  permissionButtonText: {
    color: Colors.allWhite,
    fontWeight: '600',
  },
  // ── Scanner ──────────────────────────────────────────────────────────────
  scannerSection: {
    alignItems: 'center',
    marginBottom: 24,
    gap: 24,
  },
  cameraBox: {
    width: '100%',
    height: 280,
    borderRadius: 24,
    overflow: 'hidden',
    position: 'relative',
  },
  qrFrameOverlay: {
    ...StyleSheet.absoluteFill,
    justifyContent: 'center',
    alignItems: 'center',
  },
  // Corner markers
  corner: {
    position: 'absolute',
    width: CORNER_SIZE,
    height: CORNER_SIZE,
    borderColor: Colors.allWhite,
  },
  cornerTL: {
    top: '20%',
    left: '15%',
    borderTopWidth: CORNER_THICKNESS,
    borderLeftWidth: CORNER_THICKNESS,
  },
  cornerTR: {
    top: '20%',
    right: '15%',
    borderTopWidth: CORNER_THICKNESS,
    borderRightWidth: CORNER_THICKNESS,
  },
  cornerBL: {
    bottom: '20%',
    left: '15%',
    borderBottomWidth: CORNER_THICKNESS,
    borderLeftWidth: CORNER_THICKNESS,
  },
  cornerBR: {
    bottom: '20%',
    right: '15%',
    borderBottomWidth: CORNER_THICKNESS,
    borderRightWidth: CORNER_THICKNESS,
  },
  scanLine: {
    position: 'absolute',
    left: '15%',
    width: '70%',
    height: 2,
    backgroundColor: Colors.primary,
  },
  processingOverlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 12,
  },
  processingText: {
    fontSize: 14,
    color: Colors.allWhite,
  },
  manualTrigger: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderWidth: 1,
    borderColor: Colors.primary,
    borderRadius: 100,
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  manualTriggerText: {
    color: Colors.primary,
    fontSize: 14,
    fontWeight: '600',
  },
  // ── History ──────────────────────────────────────────────────────────────
  historyHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 14,
    marginTop: 8,
  },
  historyTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  seeAllText: {
    color: Colors.primary,
    fontSize: 14,
    fontWeight: '600',
  },
  historyErrorBox: {
    alignItems: 'center',
    paddingVertical: 20,
    gap: 8,
  },
  historyErrorText: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    textAlign: 'center',
  },
  retryText: {
    color: Colors.primary,
    fontSize: 14,
    fontWeight: '600',
  },
  emptyHistoryCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 24,
    alignItems: 'center',
    gap: 10,
  },
  emptyHistoryText: {
    color: Colors.textSecondaryDark,
    fontSize: 14,
  },
  historyList: {},
  historyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    gap: 12,
  },
  historyIconBox: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(255,96,71,0.1)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  historyRowTitle: {
    fontSize: 15,
    fontWeight: '500',
    color: Colors.textDark,
  },
  historyRowDate: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
  },
  historyRowTime: {
    fontSize: 14,
    fontWeight: '500',
    color: Colors.textDark,
  },
  historyDivider: {
    height: 1,
    backgroundColor: Colors.borderDark,
    marginLeft: 44 + 12,
    opacity: 0.5,
  },
  seeAllBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,96,71,0.08)',
    borderRadius: 12,
    paddingVertical: 14,
    marginTop: 4,
    gap: 4,
  },
  seeAllBannerText: {
    fontSize: 14,
    fontWeight: '500',
    color: Colors.primary,
  },
  // ── Error modal ──────────────────────────────────────────────────────────
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  errorModal: {
    width: '90%',
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 24,
    alignItems: 'center',
  },
  errorModalTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
    marginBottom: 8,
  },
  errorModalMsg: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 20,
  },
  errorModalBtn: {
    paddingHorizontal: 32,
    paddingVertical: 10,
  },
  errorModalBtnText: {
    color: Colors.primary,
    fontWeight: '600',
    fontSize: 15,
  },
});
