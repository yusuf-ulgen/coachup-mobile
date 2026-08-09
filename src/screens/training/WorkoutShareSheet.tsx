import React, { useState, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Modal,
  Image,
  Dimensions,
  useWindowDimensions,
  ScrollView,
  Share,
  ActivityIndicator,
} from 'react-native';
import { X, Image as ImageIcon, Share2, Trash2, Check } from 'lucide-react-native';
import * as ImagePicker from 'expo-image-picker';
import * as Sharing from 'expo-sharing';
import ViewShot, { captureRef } from 'react-native-view-shot';
import Svg, { Path, Circle, Line } from 'react-native-svg';
import { Colors } from '../../theme/colors';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const normalizeRoutePoints = (
  points: Array<{ latitude: number; longitude: number } | [number, number]>,
  width: number,
  height: number,
  padding: number = 48
) => {
  if (!points || points.length < 2) return [];

  const parsed = points.map((p) => {
    if (Array.isArray(p)) return { lat: p[0], lng: p[1] };
    return { lat: p.latitude || (p as any).lat || 0, lng: p.longitude || (p as any).lng || 0 };
  });

  let minLat = parsed[0].lat;
  let maxLat = parsed[0].lat;
  let minLng = parsed[0].lng;
  let maxLng = parsed[0].lng;

  for (const pt of parsed) {
    if (pt.lat < minLat) minLat = pt.lat;
    if (pt.lat > maxLat) maxLat = pt.lat;
    if (pt.lng < minLng) minLng = pt.lng;
    if (pt.lng > maxLng) maxLng = pt.lng;
  }

  const dLng = Math.max(maxLng - minLng, 0.00001);
  const dLat = Math.max(maxLat - minLat, 0.00001);

  const drawW = Math.max(width - 2 * padding, 1);
  const drawH = Math.max(height - 2 * padding, 1);

  const scale = Math.min(drawW / dLng, drawH / dLat);
  const offsetX = padding + (drawW - dLng * scale) / 2;
  const offsetY = padding + (drawH - dLat * scale) / 2;

  return parsed.map((p) => ({
    x: offsetX + (p.lng - minLng) * scale,
    y: offsetY + (maxLat - p.lat) * scale,
  }));
};

const createSvgPath = (coords: Array<{ x: number; y: number }>) => {
  if (!coords || coords.length < 2) return '';
  let path = `M ${coords[0].x.toFixed(1)} ${coords[0].y.toFixed(1)}`;
  for (let i = 1; i < coords.length; i++) {
    path += ` L ${coords[i].x.toFixed(1)} ${coords[i].y.toFixed(1)}`;
  }
  return path;
};

const ShareRouteSvg: React.FC<{
  routePoints: Array<{ latitude: number; longitude: number } | [number, number]>;
  width: number;
  height: number;
  transparentBg?: boolean;
}> = ({ routePoints, width, height, transparentBg = false }) => {
  const normalized = normalizeRoutePoints(routePoints, width, height, 48);
  if (normalized.length < 2) return null;

  const pathStr = createSvgPath(normalized);
  const first = normalized[0];
  const last = normalized[normalized.length - 1];

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      {!transparentBg && (
        <Svg width={width} height={height} style={StyleSheet.absoluteFill}>
          {Array.from({ length: Math.ceil(width / 36) }).map((_, i) => (
            <Line
              key={`v-${i}`}
              x1={i * 36}
              y1={0}
              x2={i * 36}
              y2={height}
              stroke="rgba(255,255,255,0.04)"
              strokeWidth={1}
            />
          ))}
          {Array.from({ length: Math.ceil(height / 36) }).map((_, i) => (
            <Line
              key={`h-${i}`}
              x1={0}
              y1={i * 36}
              x2={width}
              y2={i * 36}
              stroke="rgba(255,255,255,0.04)"
              strokeWidth={1}
            />
          ))}
        </Svg>
      )}

      <Svg width={width} height={height} style={StyleSheet.absoluteFill}>
        <Path
          d={pathStr}
          stroke={Colors.primary}
          strokeWidth={10}
          strokeLinecap="round"
          strokeLinejoin="round"
          opacity={0.35}
        />
        <Path
          d={pathStr}
          stroke={Colors.primary}
          strokeWidth={4}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <Circle cx={first.x} cy={first.y} r={5} fill="#4CAF50" stroke="#FFFFFF" strokeWidth={1.5} />
        <Circle cx={last.x} cy={last.y} r={5} fill={Colors.primary} stroke="#FFFFFF" strokeWidth={1.5} />
      </Svg>
    </View>
  );
};

export enum ShareTemplate {
  MAP_FOCUSED = 'MAP_FOCUSED',
  DETAILED = 'DETAILED',
  SIMPLE = 'SIMPLE',
  CARD_STORY = 'CARD_STORY',
}

const TEMPLATES = [
  {
    id: ShareTemplate.MAP_FOCUSED,
    title: 'Rota Odaklı',
    desc: 'Harita ve rota çizimini ön plana çıkarır',
  },
  {
    id: ShareTemplate.DETAILED,
    title: 'Detaylı İstatistik',
    desc: 'Tüm performans verilerini şık kartlarda sunar',
  },
  {
    id: ShareTemplate.SIMPLE,
    title: 'Minimal',
    desc: 'Sadece mesafe ve süre vurgusu yapar',
  },
  {
    id: ShareTemplate.CARD_STORY,
    title: 'Hikaye Kartı',
    desc: 'Koyu arka plan ile sosyal medya için ideal kart',
  },
];

const formatShareTime = (seconds: number): String => {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) {
    return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
};

const formatShareDistance = (distanceKm: number): string => {
  return `${distanceKm.toFixed(2)} km`;
};

const formatShareSpeed = (paceMinPerKm: number, speedKmh?: number): string => {
  if (speedKmh && speedKmh > 0) {
    return `${speedKmh.toFixed(1)} km/s`;
  }
  if (paceMinPerKm && paceMinPerKm > 0.05 && paceMinPerKm < 60) {
    const calcSpeed = 60 / paceMinPerKm;
    return `${calcSpeed.toFixed(1)} km/s`;
  }
  return `0.0 km/s`;
};

export const WorkoutShareSheet: React.FC<{
  visible: boolean;
  training: any;
  durationSeconds: number;
  distanceKm?: number;
  totalCalories?: number;
  avgPaceMinPerKm?: number;
  avgSpeedKmh?: number;
  routePoints?: Array<{ latitude: number; longitude: number } | [number, number]>;
  onDismiss: () => void;
}> = ({
  visible,
  training,
  durationSeconds = 0,
  distanceKm = 0,
  totalCalories = 0,
  avgPaceMinPerKm = 0,
  avgSpeedKmh = 0,
  routePoints = [],
  onDismiss,
}) => {
  const insets = useSafeAreaInsets();
  const [activeTemplate, setActiveTemplate] = useState<number>(0);
  const [backgroundUri, setBackgroundUri] = useState<string | null>(null);
  const [showRouteLine, setShowRouteLine] = useState<boolean>(true);
  const [isSharing, setIsSharing] = useState<boolean>(false);
  const cardViewRef = useRef<any>(null);

  const { width: windowWidth, height: windowHeight } = useWindowDimensions();
  // Reserved height: Top bar (~56px) + Footer (~150px) + Pagination/Title/Desc (~80px) + Safe padding (~74px) = 360px
  const maxCardHeight = Math.max(windowHeight - 380, 240);
  const cardWidthFromHeight = maxCardHeight * (9 / 16);
  const cardWidthFromWidth = windowWidth * 0.72;
  const cardWidth = Math.min(cardWidthFromWidth, cardWidthFromHeight, 280);
  const cardHeight = cardWidth * (16 / 9);

  const title = (training?.title || 'Fitness').toUpperCase();
  const durationStr = formatShareTime(durationSeconds);
  const distanceStr = formatShareDistance(distanceKm);
  const speedStr = formatShareSpeed(avgPaceMinPerKm, avgSpeedKmh);
  const showDistance = distanceKm > 0.01;
  const activeRoutePoints =
    routePoints && routePoints.length > 1
      ? routePoints
      : training?.route_points || training?.routePoints || [];

  const handlePickImage = async () => {
    try {
      const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permission.granted) {
        return;
      }
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'],
        allowsEditing: true,
        aspect: [9, 16],
        quality: 1,
      });

      if (!result.canceled && result.assets && result.assets.length > 0) {
        setBackgroundUri(result.assets[0].uri);
      }
    } catch (e) {
      console.error('Image pick error:', e);
    }
  };

  const handleShare = async () => {
    if (isSharing) return;
    setIsSharing(true);

    try {
      if (cardViewRef.current) {
        const uri = await captureRef(cardViewRef.current, {
          format: 'png',
          quality: 0.95,
          result: 'tmpfile',
        });

        const isSharingAvailable = await Sharing.isAvailableAsync();
        if (isSharingAvailable) {
          await Sharing.shareAsync(uri, {
            mimeType: 'image/png',
            dialogTitle: 'Antrenmanını Paylaş',
            UTI: 'public.png',
          });
        } else {
          await Share.share({
            message: `CoachUP ile ${training?.title || 'Fitness'} antrenmanımı tamamladım! Süre: ${durationStr}`,
          });
        }
      }
    } catch (e) {
      console.error('Share error:', e);
      try {
        await Share.share({
          message: `CoachUP ile ${training?.title || 'Fitness'} antrenmanımı tamamladım! Süre: ${durationStr}`,
        });
      } catch (err) {
        console.error('Fallback share error:', err);
      }
    } finally {
      setIsSharing(false);
    }
  };

  if (!visible) return null;

  // ── RENDER CARD CONTENT DEPENDING ON TEMPLATE ────────────────────────────
  const renderCardContent = (tplId: ShareTemplate) => {
    switch (tplId) {
      case ShareTemplate.MAP_FOCUSED:
        return (
          <View style={styles.cardPaddingContainer}>
            {/* Top Logo */}
            <View style={styles.topLogoRow}>
              <Image
                source={require('../../../assets/coach_logo.png')}
                style={styles.logoImage}
                resizeMode="contain"
              />
            </View>

            {/* Bottom Stats */}
            <View style={styles.bottomStatsContainer}>
              <Text style={styles.categoryTitleText}>{title}</Text>
              {showDistance && (
                <Text style={styles.heroDistanceText}>{distanceStr}</Text>
              )}
              <View style={styles.rowStatsGroup}>
                <View style={styles.statColLeft}>
                  <Text style={styles.statValSmall} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>{durationStr}</Text>
                  <Text style={styles.statLblSmall}>Süre</Text>
                </View>
                <View style={styles.statColCenter}>
                  <Text style={styles.statValSmall} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>{speedStr}</Text>
                  <Text style={styles.statLblSmall}>Hız</Text>
                </View>
                {totalCalories > 0 && (
                  <View style={styles.statColRight}>
                    <Text style={styles.statValSmall} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>{totalCalories} kcal</Text>
                    <Text style={styles.statLblSmall}>Kalori</Text>
                  </View>
                )}
              </View>
            </View>
          </View>
        );

      case ShareTemplate.DETAILED:
        return (
          <View style={[styles.cardPaddingContainer, { justifyContent: 'flex-end' }]}>
            <View style={{ gap: 12 }}>
              <Image
                source={require('../../../assets/coach_logo.png')}
                style={styles.logoImageSmall}
                resizeMode="contain"
              />
              <View>
                <Text style={styles.categoryTitleTextDetailed}>{title}</Text>
                <Text style={styles.subtitleText}>Antrenman tamamlandı</Text>
              </View>

              {showDistance && (
                <Text style={styles.heroDistanceText}>{distanceStr}</Text>
              )}

              <View style={styles.chipsRow}>
                <View style={styles.metricChip}>
                  <Text style={styles.metricChipVal} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>{durationStr}</Text>
                  <Text style={styles.metricChipLbl}>Süre</Text>
                </View>
                {showDistance && (
                  <View style={styles.metricChip}>
                    <Text style={styles.metricChipVal} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>{distanceStr}</Text>
                    <Text style={styles.metricChipLbl}>Mesafe</Text>
                  </View>
                )}
                <View style={styles.metricChip}>
                  <Text style={styles.metricChipVal} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>{speedStr}</Text>
                  <Text style={styles.metricChipLbl}>Hız</Text>
                </View>
                {totalCalories > 0 && (
                  <View style={styles.metricChip}>
                    <Text style={styles.metricChipVal} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>{totalCalories}</Text>
                    <Text style={styles.metricChipLbl}>Kalori</Text>
                  </View>
                )}
              </View>
            </View>
          </View>
        );

      case ShareTemplate.SIMPLE:
        return (
          <View style={[styles.cardPaddingContainer, { alignItems: 'center', justifyContent: 'space-between' }]}>
            <View style={{ alignItems: 'center' }}>
              <Image
                source={require('../../../assets/coach_logo.png')}
                style={styles.logoImage}
                resizeMode="contain"
              />
              <Text style={[styles.categoryTitleText, { marginTop: 4, textAlign: 'center' }]}>{title}</Text>
            </View>

            <View style={{ alignItems: 'center' }}>
              <Text style={styles.simpleLabel}>{showDistance ? 'MESAFE' : 'Süre'}</Text>
              <Text style={styles.simpleHeroVal}>{showDistance ? distanceStr : durationStr}</Text>
            </View>

            <Text style={styles.simpleFooterText}>Antrenman Tamamlandı</Text>
          </View>
        );

      case ShareTemplate.CARD_STORY:
      default:
        return (
          <View style={styles.storyCardOuterContainer}>
            <View style={styles.storyCardBox}>
              <Image
                source={require('../../../assets/coach_logo.png')}
                style={styles.logoImageSmall}
                resizeMode="contain"
              />
              <Text style={styles.storyCardCategory}>{title}</Text>

              <View style={styles.storyCardDivider} />

              {showDistance && (
                <View style={{ alignItems: 'center', marginVertical: 6 }}>
                  <Text style={styles.storyCardDistanceLbl}>MESAFE</Text>
                  <Text style={styles.storyCardDistanceVal}>{distanceStr}</Text>
                </View>
              )}

              <View style={styles.storyCardBottomRow}>
                <View style={styles.storyCardCol}>
                  <Text style={styles.storyCardRowLbl}>SÜRE</Text>
                  <Text style={styles.storyCardRowVal} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>{durationStr}</Text>
                </View>

                <View style={styles.storyCardCol}>
                  <Text style={styles.storyCardRowLbl}>HIZ</Text>
                  <Text style={styles.storyCardRowVal} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>{speedStr}</Text>
                </View>

                {totalCalories > 0 && (
                  <View style={styles.storyCardCol}>
                    <Text style={styles.storyCardRowLbl}>KALORİ</Text>
                    <Text style={styles.storyCardRowVal} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>{totalCalories}</Text>
                  </View>
                )}
              </View>
            </View>
          </View>
        );
    }
  };

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onDismiss}>
      <View style={[styles.container, { paddingTop: Math.max(16, insets.top + 8) }]}>
        {/* ── Top Bar ──────────────────────────────────────────────────────── */}
        <View style={styles.header}>
          <TouchableOpacity style={styles.iconButton} onPress={onDismiss} activeOpacity={0.8}>
            <X size={20} color={Colors.allWhite} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>Aktiviteyi Paylaş</Text>
          <View style={{ width: 36 }} />
        </View>

        {/* ── Pager & Cards Preview Area ────────────────────────────────────── */}
        <View style={styles.previewContainer}>
          <ScrollView
            horizontal
            pagingEnabled
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={{ alignItems: 'center', paddingVertical: 8 }}
            onMomentumScrollEnd={(e) => {
              const idx = Math.round(e.nativeEvent.contentOffset.x / windowWidth);
              if (idx >= 0 && idx < TEMPLATES.length) {
                setActiveTemplate(idx);
              }
            }}
          >
            {TEMPLATES.map((tpl, i) => (
              <View key={tpl.id} style={[styles.page, { width: windowWidth }]}>
                <View
                  style={[styles.cardWrapper, { width: cardWidth, height: cardHeight }]}
                  collapsable={false}
                  ref={i === activeTemplate ? cardViewRef : null}
                >
                  {/* Background Image or Gradient Background */}
                  {backgroundUri ? (
                    <Image
                      source={{ uri: backgroundUri }}
                      style={StyleSheet.absoluteFill}
                      resizeMode="cover"
                    />
                  ) : (
                    <View style={styles.gradientDefaultBg} />
                  )}

                  {/* GPS Route Overlay (Koşu, Yürüyüş, Bisiklet, Yüzme, Hyrox vb.) */}
                  {showRouteLine && activeRoutePoints && activeRoutePoints.length >= 2 && (
                    <ShareRouteSvg
                      routePoints={activeRoutePoints}
                      width={cardWidth}
                      height={cardHeight}
                      transparentBg={!!backgroundUri}
                    />
                  )}

                  {/* Dark Contrast Overlay Layer */}
                  <View style={styles.overlayGradient} />

                  {/* Template Content */}
                  {renderCardContent(tpl.id as ShareTemplate)}
                </View>
              </View>
            ))}
          </ScrollView>

          {/* Pagination Indicators */}
          <View style={styles.pagination}>
            {TEMPLATES.map((_, i) => (
              <View
                key={i}
                style={[styles.dot, activeTemplate === i && styles.dotActive]}
              />
            ))}
          </View>

          {/* Active Template Title & Description */}
          <Text style={styles.templateTitle}>{TEMPLATES[activeTemplate].title}</Text>
          <Text style={styles.templateDesc}>{TEMPLATES[activeTemplate].desc}</Text>

          {/* GPS Route Toggle Checkbox */}
          {activeRoutePoints && activeRoutePoints.length >= 2 && (
            <TouchableOpacity
              style={styles.routeToggleRow}
              onPress={() => setShowRouteLine((prev) => !prev)}
              activeOpacity={0.8}
            >
              <View style={[styles.checkboxSquare, showRouteLine && styles.checkboxSquareActive]}>
                {showRouteLine && <Check size={12} color="#FFFFFF" strokeWidth={3} />}
              </View>
              <Text style={styles.routeToggleText}>Harita Rotasını Göster</Text>
            </TouchableOpacity>
          )}
        </View>

        {/* ── Footer Buttons ────────────────────────────────────────────────── */}
        <View style={[styles.footer, { paddingBottom: Math.max(20, insets.bottom + 12) }]}>
          {backgroundUri ? (
            <View style={styles.twoButtonRow}>
              <TouchableOpacity
                style={[styles.secondaryButton, { flex: 1, marginBottom: 0 }]}
                onPress={handlePickImage}
                activeOpacity={0.8}
              >
                <ImageIcon size={16} color={Colors.allWhite} />
                <Text style={[styles.secondaryButtonText, { fontSize: 13, marginLeft: 6 }]}>
                  Fotoğrafı Değiştir
                </Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.removePhotoButton}
                onPress={() => setBackgroundUri(null)}
                activeOpacity={0.8}
              >
                <Trash2 size={16} color="#FF453A" />
                <Text style={styles.removePhotoButtonText}>Fotoğrafı Kaldır</Text>
              </TouchableOpacity>
            </View>
          ) : (
            <TouchableOpacity
              style={styles.secondaryButton}
              onPress={handlePickImage}
              activeOpacity={0.8}
            >
              <ImageIcon size={18} color={Colors.allWhite} />
              <Text style={styles.secondaryButtonText}>Arka Plan Fotoğrafı Ekle</Text>
            </TouchableOpacity>
          )}

          <TouchableOpacity
            style={[styles.primaryButton, isSharing && { opacity: 0.7 }]}
            onPress={handleShare}
            disabled={isSharing}
            activeOpacity={0.85}
          >
            {isSharing ? (
              <ActivityIndicator size="small" color={Colors.allWhite} />
            ) : (
              <>
                <Share2 size={18} color={Colors.allWhite} />
                <Text style={styles.primaryButtonText}>Görseli Paylaş</Text>
              </>
            )}
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0A0A0F',
    paddingTop: 48,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  iconButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255,255,255,0.1)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  previewContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  page: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardWrapper: {
    borderRadius: 20,
    overflow: 'hidden',
    borderWidth: 1.5,
    borderColor: 'rgba(255, 255, 255, 0.25)',
    backgroundColor: '#121216',
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.4,
    shadowRadius: 12,
  },
  gradientDefaultBg: {
    ...StyleSheet.absoluteFill,
    backgroundColor: '#18102B',
  },
  overlayGradient: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(0, 0, 0, 0.45)',
  },
  cardPaddingContainer: {
    flex: 1,
    padding: 20,
    justifyContent: 'space-between',
  },
  topLogoRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  logoImage: {
    width: 80,
    height: 24,
    tintColor: undefined,
  },
  logoImageSmall: {
    width: 72,
    height: 20,
  },
  bottomStatsContainer: {
    gap: 8,
  },
  categoryTitleText: {
    fontSize: 11,
    fontWeight: '700',
    color: Colors.primary,
    letterSpacing: 1.5,
  },
  categoryTitleTextDetailed: {
    fontSize: 11,
    fontWeight: '700',
    color: 'rgba(255,255,255,0.7)',
    letterSpacing: 1.2,
  },
  subtitleText: {
    fontSize: 12,
    color: 'rgba(255,255,255,0.55)',
    marginTop: 2,
  },
  heroDistanceText: {
    fontSize: 38,
    fontWeight: '900',
    color: Colors.allWhite,
  },
  rowStatsGroup: {
    flexDirection: 'row',
    width: '100%',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  statColLeft: {
    flex: 1,
    alignItems: 'flex-start',
  },
  statColCenter: {
    flex: 1,
    alignItems: 'center',
  },
  statColRight: {
    flex: 1,
    alignItems: 'flex-end',
  },
  statValSmall: {
    fontSize: 13.5,
    fontWeight: '700',
    color: Colors.allWhite,
    paddingHorizontal: 1,
  },
  statLblSmall: {
    fontSize: 10,
    color: 'rgba(255,255,255,0.6)',
  },
  chipsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 4,
  },
  metricChip: {
    flex: 1,
    minWidth: '40%',
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderRadius: 10,
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
  metricChipVal: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.allWhite,
  },
  metricChipLbl: {
    fontSize: 9,
    color: 'rgba(255,255,255,0.6)',
    letterSpacing: 0.5,
    marginTop: 2,
  },
  simpleLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.primary,
    letterSpacing: 1,
  },
  simpleHeroVal: {
    fontSize: 50,
    fontWeight: '900',
    color: Colors.allWhite,
    marginTop: 4,
  },
  simpleFooterText: {
    fontSize: 12,
    color: 'rgba(255,255,255,0.45)',
  },
  storyCardOuterContainer: {
    flex: 1,
    padding: 16,
    justifyContent: 'center',
    alignItems: 'center',
  },
  storyCardBox: {
    width: '100%',
    backgroundColor: 'rgba(0, 0, 0, 0.75)',
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(255, 96, 71, 0.4)',
    padding: 20,
    alignItems: 'center',
    gap: 10,
  },
  storyCardCategory: {
    fontSize: 12,
    fontWeight: '700',
    color: 'rgba(255,255,255,0.7)',
    letterSpacing: 1.5,
  },
  storyCardDivider: {
    width: '100%',
    height: 1,
    backgroundColor: 'rgba(255,255,255,0.1)',
    marginVertical: 4,
  },
  storyCardDistanceLbl: {
    fontSize: 10,
    fontWeight: '700',
    color: Colors.primary,
  },
  storyCardDistanceVal: {
    fontSize: 34,
    fontWeight: '900',
    color: Colors.allWhite,
  },
  storyCardBottomRow: {
    flexDirection: 'row',
    width: '100%',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 4,
  },
  storyCardCol: {
    flex: 1,
    alignItems: 'center',
    paddingHorizontal: 2,
  },
  storyCardRowLbl: {
    fontSize: 9,
    fontWeight: '700',
    color: 'rgba(255,255,255,0.5)',
  },
  storyCardRowVal: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.allWhite,
    marginTop: 2,
  },
  pagination: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginVertical: 14,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(255,255,255,0.3)',
    marginHorizontal: 3,
  },
  dotActive: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: Colors.primary,
  },
  templateTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: Colors.allWhite,
    marginBottom: 4,
  },
  templateDesc: {
    fontSize: 12,
    color: 'rgba(255,255,255,0.5)',
    textAlign: 'center',
    paddingHorizontal: 32,
  },
  routeToggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 8,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
  },
  checkboxSquare: {
    width: 18,
    height: 18,
    borderRadius: 4,
    borderWidth: 1.5,
    borderColor: 'rgba(255, 255, 255, 0.4)',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 8,
  },
  checkboxSquareActive: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  routeToggleText: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.allWhite,
  },
  footer: {
    paddingHorizontal: 20,
    paddingBottom: 32,
    paddingTop: 16,
  },
  secondaryButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    height: 48,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.15)',
    backgroundColor: 'rgba(255,255,255,0.06)',
    marginBottom: 10,
  },
  secondaryButtonText: {
    color: Colors.allWhite,
    fontSize: 14,
    fontWeight: '500',
    marginLeft: 8,
  },
  twoButtonRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 10,
  },
  removePhotoButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    height: 48,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: 'rgba(255, 69, 58, 0.35)',
    backgroundColor: 'rgba(255, 69, 58, 0.12)',
    paddingHorizontal: 14,
  },
  removePhotoButtonText: {
    color: '#FF453A',
    fontSize: 13,
    fontWeight: '600',
    marginLeft: 6,
  },
  primaryButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    height: 52,
    borderRadius: 14,
    backgroundColor: Colors.primary,
  },
  primaryButtonText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: 'bold',
    marginLeft: 8,
  },
});
