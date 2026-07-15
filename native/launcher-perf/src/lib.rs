//! Dock focus-outline mask generation (alpha threshold + disk dilate / hollow).
//!
//! Port of the former Java `SuggestionBarView.buildFocusOutlineMask` hot path so
//! the O(radius²) canvas-draw dilate runs in native code.

#![allow(clippy::needless_range_loop)]

/// ARGB packed pixel helpers (Android `Bitmap` / `int[]` layout: 0xAARRGGBB).
#[inline]
pub fn alpha(pixel: u32) -> u8 {
    (pixel >> 24) as u8
}

#[inline]
fn solid_white() -> u32 {
    0xFFFF_FFFF
}

#[inline]
fn transparent() -> u32 {
    0x0000_0000
}

/// Result of focus-outline construction.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct FocusOutlineMask {
    pub width: i32,
    pub height: i32,
    /// Packed ARGB pixels, row-major, length = width * height.
    pub pixels: Vec<u32>,
}

/// Build a hollow focus outline mask from an ARGB source buffer.
///
/// Matches the Java behavior:
/// 1. threshold alpha relative to max alpha in the buffer
/// 2. dilate the binary silhouette by `gap + stroke`
/// 3. punch out the interior by dilating with radius `gap` (or the solid binary if gap == 0)
pub fn build_focus_outline_mask(
    src: &[u32],
    width: i32,
    height: i32,
    gap: i32,
    stroke: i32,
) -> Result<FocusOutlineMask, &'static str> {
    if width <= 0 || height <= 0 {
        return Err("width and height must be positive");
    }
    let w = width as usize;
    let h = height as usize;
    let expected = w.checked_mul(h).ok_or("dimensions overflow")?;
    if src.len() < expected {
        return Err("pixel buffer shorter than width*height");
    }

    let safe_gap = gap.max(0) as usize;
    let safe_stroke = stroke.max(1) as usize;
    let outer = safe_gap + safe_stroke;

    let mut max_alpha: u8 = 0;
    for i in 0..expected {
        max_alpha = max_alpha.max(alpha(src[i]));
    }
    let threshold = (max_alpha as f32 * 0.25f32).round() as i32;
    let threshold = threshold.max(8) as u8;

    let mut binary = vec![false; expected];
    for i in 0..expected {
        binary[i] = alpha(src[i]) >= threshold;
    }

    let out_w = w + outer * 2;
    let out_h = h + outer * 2;
    let out_len = out_w
        .checked_mul(out_h)
        .ok_or("output dimensions overflow")?;
    let mut result = vec![transparent(); out_len];

    // Outer dilate: paint solid disk of radius `outer` for every ON binary pixel.
    dilate_or_into(&binary, w, h, &mut result, out_w, out_h, outer, outer, solid_white());

    if safe_gap > 0 {
        // Hollow: punch dilated (radius = gap) silhouette out of the outer ring.
        dilate_or_into(
            &binary,
            w,
            h,
            &mut result,
            out_w,
            out_h,
            outer,
            safe_gap,
            transparent(),
        );
    } else {
        // gap == 0: punch the solid binary (no extra dilate) at origin.
        for y in 0..h {
            for x in 0..w {
                if binary[y * w + x] {
                    let ox = x + outer;
                    let oy = y + outer;
                    result[oy * out_w + ox] = transparent();
                }
            }
        }
    }

    Ok(FocusOutlineMask {
        width: out_w as i32,
        height: out_h as i32,
        pixels: result,
    })
}

/// Disk dilate: for each ON source pixel, write `paint` into every output pixel
/// whose offset from (x+origin, y+origin) lies inside the radius disk.
fn dilate_or_into(
    binary: &[bool],
    src_w: usize,
    src_h: usize,
    out: &mut [u32],
    out_w: usize,
    out_h: usize,
    origin: usize,
    radius: usize,
    paint: u32,
) {
    if radius == 0 {
        for y in 0..src_h {
            for x in 0..src_w {
                if !binary[y * src_w + x] {
                    continue;
                }
                let ox = x + origin;
                let oy = y + origin;
                if ox < out_w && oy < out_h {
                    out[oy * out_w + ox] = paint;
                }
            }
        }
        return;
    }

    let r = radius as i32;
    let r2 = r * r;
    let out_w_i = out_w as i32;
    let out_h_i = out_h as i32;
    let origin_i = origin as i32;

    for y in 0..src_h {
        for x in 0..src_w {
            if !binary[y * src_w + x] {
                continue;
            }
            let cx = x as i32 + origin_i;
            let cy = y as i32 + origin_i;
            for dy in -r..=r {
                let yy = cy + dy;
                if yy < 0 || yy >= out_h_i {
                    continue;
                }
                let dy2 = dy * dy;
                for dx in -r..=r {
                    if dx * dx + dy2 > r2 {
                        continue;
                    }
                    let xx = cx + dx;
                    if xx < 0 || xx >= out_w_i {
                        continue;
                    }
                    out[(yy as usize) * out_w + (xx as usize)] = paint;
                }
            }
        }
    }
}

/// Count non-transparent pixels (alpha > 0).
pub fn count_opaque(pixels: &[u32]) -> usize {
    pixels.iter().filter(|p| alpha(**p) > 0).count()
}

#[cfg(feature = "jni")]
mod jni_bridge {
    use super::*;
    use jni::objects::{JClass, JIntArray};
    use jni::sys::jintArray;
    use jni::JNIEnv;

    /// JNI: `int[] buildFocusOutlineMask(int[] argb, int width, int height, int gap, int stroke)`
    ///
    /// Return layout: `[outWidth, outHeight, pixel0, pixel1, ...]`
    #[no_mangle]
    pub extern "system" fn Java_com_termux_app_nativebridge_LauncherPerfNative_buildFocusOutlineMask<'local>(
        mut env: JNIEnv<'local>,
        _class: JClass<'local>,
        argb: JIntArray<'local>,
        width: i32,
        height: i32,
        gap: i32,
        stroke: i32,
    ) -> jintArray {
        let result = (|| -> Result<jintArray, String> {
            if width <= 0 || height <= 0 {
                return Err("invalid size".into());
            }
            let len = (width as i64)
                .checked_mul(height as i64)
                .ok_or_else(|| "size overflow".to_string())? as usize;

            let arr_len = env
                .get_array_length(&argb)
                .map_err(|e| format!("get_array_length: {e}"))? as usize;
            if arr_len < len {
                return Err("argb shorter than width*height".into());
            }

            let mut buf = vec![0i32; len];
            env.get_int_array_region(&argb, 0, &mut buf)
                .map_err(|e| format!("get_int_array_region: {e}"))?;

            // Android int[] is signed; treat bit-pattern as ARGB u32.
            let pixels: Vec<u32> = buf.iter().map(|v| *v as u32).collect();
            let mask = build_focus_outline_mask(&pixels, width, height, gap, stroke)
                .map_err(|e| e.to_string())?;

            let out_len = 2 + mask.pixels.len();
            let out = env
                .new_int_array(out_len as i32)
                .map_err(|e| format!("new_int_array: {e}"))?;

            let mut packed = Vec::with_capacity(out_len);
            packed.push(mask.width);
            packed.push(mask.height);
            packed.extend(mask.pixels.iter().map(|p| *p as i32));

            env.set_int_array_region(&out, 0, &packed)
                .map_err(|e| format!("set_int_array_region: {e}"))?;
            Ok(out.into_raw())
        })();

        match result {
            Ok(arr) => arr,
            Err(msg) => {
                let _ = env.throw_new("java/lang/IllegalArgumentException", msg);
                std::ptr::null_mut()
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn solid_rect(w: i32, h: i32, fill: u32) -> Vec<u32> {
        vec![fill; (w * h) as usize]
    }

    #[test]
    fn filled_rect_yields_nonempty_outline() {
        let w = 16i32;
        let h = 16i32;
        let src = solid_rect(w, h, 0xFF00_00FF); // opaque blue
        let mask = build_focus_outline_mask(&src, w, h, 2, 2).expect("mask");
        assert_eq!(mask.width, w + 2 * (2 + 2));
        assert_eq!(mask.height, h + 2 * (2 + 2));
        let opaque = count_opaque(&mask.pixels);
        assert!(
            opaque > 0,
            "expected non-empty outline for solid rect, got 0 opaque pixels"
        );
        // Hollow ring: interior should not be fully filled solid
        let total = mask.pixels.len();
        assert!(
            opaque < total,
            "outline should be hollow (opaque={opaque} total={total})"
        );
    }

    #[test]
    fn all_transparent_yields_empty_mask() {
        let w = 12i32;
        let h = 10i32;
        let src = solid_rect(w, h, 0x0000_0000);
        let mask = build_focus_outline_mask(&src, w, h, 1, 2).expect("mask");
        assert_eq!(count_opaque(&mask.pixels), 0);
    }

    #[test]
    fn thin_ring_source_still_outlines() {
        // 1-px ring of opaque pixels on a transparent field.
        let w = 20i32;
        let h = 20i32;
        let mut src = solid_rect(w, h, 0x0000_0000);
        for x in 4..16 {
            src[(4 * w + x) as usize] = 0xFFFF_FFFF;
            src[(15 * w + x) as usize] = 0xFFFF_FFFF;
        }
        for y in 4..16 {
            src[(y * w + 4) as usize] = 0xFFFF_FFFF;
            src[(y * w + 15) as usize] = 0xFFFF_FFFF;
        }
        let mask = build_focus_outline_mask(&src, w, h, 1, 2).expect("mask");
        assert!(
            count_opaque(&mask.pixels) > 0,
            "thin ring must produce a non-empty outline"
        );
    }

    #[test]
    fn gap_zero_punches_solid_interior() {
        let w = 8i32;
        let h = 8i32;
        let src = solid_rect(w, h, 0xFF11_2233);
        let mask = build_focus_outline_mask(&src, w, h, 0, 2).expect("mask");
        // Center of output should be transparent (punched) for a solid fill.
        let cx = mask.width / 2;
        let cy = mask.height / 2;
        let center = mask.pixels[(cy * mask.width + cx) as usize];
        assert_eq!(alpha(center), 0, "center should be hollow with gap=0");
        assert!(count_opaque(&mask.pixels) > 0);
    }

    #[test]
    fn rejects_bad_dimensions() {
        assert!(build_focus_outline_mask(&[], 0, 1, 1, 1).is_err());
        assert!(build_focus_outline_mask(&[0], 2, 2, 1, 1).is_err());
    }
}
