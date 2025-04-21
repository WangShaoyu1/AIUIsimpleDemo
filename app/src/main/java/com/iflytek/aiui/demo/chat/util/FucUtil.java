package com.iflytek.aiui.demo.chat.util;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 功能函数扩展类。
 */
public class FucUtil {
	/**
	 * 将assets目录下文件读取到字符串。
	 *
	 * @param context 上下文
	 * @param file 文件路径
	 * @param code 字符编码
	 * @return 字符串
	 */
	public static String readAssetFile(Context context, String file, String code) {
		int len;
		byte[] buf;
		String result = "";

		try {
			InputStream in = context.getAssets().open(file);
			len = in.available();
			buf = new byte[len];
			in.read(buf, 0, len);

			result = new String(buf, code);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * 将assets下的目录拷贝到目标目录。
	 *
	 * @param context 上下文
	 * @param srcName 源目录
	 * @param dstName 目标目录
	 * @return 是否成功
	 */
	public static boolean copyAssetFolder(Context context, String srcName, String dstName) {
		try {
			boolean result;
			String[] fileList = context.getAssets().list(srcName);
			if (fileList == null) return false;

			if (fileList.length == 0) {
				result = copyAssetFile(context, srcName, dstName);
			} else {
				File file = new File(dstName);
				result = file.mkdirs();
				for (String filename : fileList) {
					result &= copyAssetFolder(context, srcName + File.separator + filename,
							dstName + File.separator + filename);
				}
			}
			return result;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 将assets下面的文件拷贝到目标文件。
	 *
	 * @param context 上下文
	 * @param srcName 源文件
	 * @param dstName 目标文件
	 * @return 是否成功
	 */
	public static boolean copyAssetFile(Context context, String srcName, String dstName) {
		try {
			InputStream in = context.getAssets().open(srcName);
			File outFile = new File(dstName);

			if (!outFile.getParentFile().exists()) {
				outFile.getParentFile().mkdirs();
			}

			OutputStream out = new FileOutputStream(outFile);
			byte[] buffer = new byte[1024];
			int read;

			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}

			in.close();
			out.close();

			return true;
		} catch (IOException e) {
			e.printStackTrace();

			return false;
		}
	}
}
