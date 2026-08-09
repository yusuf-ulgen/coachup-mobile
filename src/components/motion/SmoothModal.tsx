import React, { useEffect, useRef, useState } from 'react';
import {
  Modal,
  View,
  TouchableOpacity,
  StyleSheet,
  Animated,
  Dimensions,
  StyleProp,
  ViewStyle,
  ModalProps,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MotionTokens, useReducedMotion } from '../../theme/motion';

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

export type SmoothModalVariant = 'modal' | 'bottom-sheet' | 'drawer-left' | 'fade';

export interface SmoothModalProps {
  visible: boolean;
  onClose: () => void;
  variant?: SmoothModalVariant;
  children: React.ReactNode;
  overlayStyle?: StyleProp<ViewStyle>;
  containerStyle?: StyleProp<ViewStyle>;
  closeOnBackdropPress?: boolean;
  transparent?: boolean;
  onRequestClose?: () => void;
  statusBarTranslucent?: boolean;
  accessibilityLabel?: string;
  drawerWidth?: number;
  testID?: string;
}

export const SmoothModal: React.FC<SmoothModalProps> = ({
  visible,
  onClose,
  variant = 'modal',
  children,
  overlayStyle,
  containerStyle,
  closeOnBackdropPress = true,
  transparent = true,
  onRequestClose,
  statusBarTranslucent = true,
  accessibilityLabel,
  drawerWidth = SCREEN_WIDTH * 0.75,
  testID,
}) => {
  const insets = useSafeAreaInsets();
  const isReducedMotion = useReducedMotion();
  const [mounted, setMounted] = useState(visible);

  // Animation values
  const progress = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (visible) {
      setMounted(true);
      if (isReducedMotion) {
        progress.setValue(1);
      } else {
        Animated.timing(progress, {
          toValue: 1,
          duration: MotionTokens.duration.normal,
          easing: MotionTokens.easing.enter,
          useNativeDriver: true,
        }).start();
      }
    } else if (mounted) {
      if (isReducedMotion) {
        progress.setValue(0);
        setMounted(false);
      } else {
        Animated.timing(progress, {
          toValue: 0,
          duration: MotionTokens.duration.fast,
          easing: MotionTokens.easing.exit,
          useNativeDriver: true,
        }).start(({ finished }) => {
          if (finished) {
            setMounted(false);
          }
        });
      }
    }
  }, [visible, isReducedMotion]);

  if (!mounted) return null;

  const handleBackdropPress = () => {
    if (closeOnBackdropPress) {
      onClose();
    }
  };

  const handleRequestClose = () => {
    if (onRequestClose) {
      onRequestClose();
    } else {
      onClose();
    }
  };

  // Interpolations based on variant
  const backdropOpacity = progress.interpolate({
    inputRange: [0, 1],
    outputRange: [0, 1],
  });

  const getContainerTransform = () => {
    switch (variant) {
      case 'bottom-sheet': {
        const translateY = progress.interpolate({
          inputRange: [0, 1],
          outputRange: [SCREEN_HEIGHT * 0.5, 0],
        });
        return [{ translateY }];
      }
      case 'drawer-left': {
        const translateX = progress.interpolate({
          inputRange: [0, 1],
          outputRange: [-drawerWidth, 0],
        });
        return [{ translateX }];
      }
      case 'modal': {
        const scale = progress.interpolate({
          inputRange: [0, 1],
          outputRange: [0.96, 1],
        });
        const opacity = progress.interpolate({
          inputRange: [0, 1],
          outputRange: [0, 1],
        });
        return [{ scale }];
      }
      case 'fade':
      default:
        return [];
    }
  };

  const containerOpacity = variant === 'fade' || variant === 'modal'
    ? progress.interpolate({ inputRange: [0, 1], outputRange: [0, 1] })
    : 1;

  const isDrawer = variant === 'drawer-left';
  const isBottomSheet = variant === 'bottom-sheet';

  return (
    <Modal
      visible={mounted}
      transparent={transparent}
      animationType="none"
      onRequestClose={handleRequestClose}
      statusBarTranslucent={statusBarTranslucent}
      accessibilityViewIsModal
      accessibilityLabel={accessibilityLabel}
      testID={testID}
    >
      <View
        style={[
          styles.overlayBase,
          isBottomSheet && styles.overlayBottomSheet,
          isDrawer && styles.overlayDrawer,
          overlayStyle,
        ]}
      >
        {/* Backdrop Fade */}
        <Animated.View
          style={[
            StyleSheet.absoluteFill,
            styles.backdropBg,
            { opacity: backdropOpacity },
          ]}
        >
          <TouchableOpacity
            style={StyleSheet.absoluteFill}
            activeOpacity={1}
            onPress={handleBackdropPress}
          />
        </Animated.View>

        {/* Animated Modal / Sheet / Drawer Container */}
        <Animated.View
          style={[
            isDrawer
              ? [styles.drawerContainer, { width: drawerWidth }]
              : isBottomSheet
              ? [styles.bottomSheetContainer, { paddingBottom: insets.bottom }]
              : [styles.modalCardContainer, { paddingTop: insets.top, paddingBottom: insets.bottom }],
            containerStyle,
            {
              opacity: containerOpacity,
              transform: getContainerTransform(),
            },
          ]}
        >
          {children}
        </Animated.View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlayBase: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  overlayBottomSheet: {
    justifyContent: 'flex-end',
  },
  overlayDrawer: {
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'stretch',
  },
  backdropBg: {
    backgroundColor: 'rgba(0, 0, 0, 0.65)',
  },
  modalCardContainer: {
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  bottomSheetContainer: {
    width: '100%',
  },
  drawerContainer: {
    height: '100%',
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
  },
});
