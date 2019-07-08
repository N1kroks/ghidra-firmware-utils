/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package firmware.uefi_fv;

import firmware.common.UUIDUtils;
import ghidra.app.util.bin.BinaryReader;
import ghidra.app.util.bin.ByteProvider;
import ghidra.formats.gfilesystem.GFile;
import ghidra.formats.gfilesystem.GFileImpl;
import ghidra.formats.gfilesystem.GFileSystemBase;
import ghidra.formats.gfilesystem.annotations.FileSystemInfo;
import ghidra.formats.gfilesystem.factory.GFileSystemBaseFactory;
import ghidra.util.Msg;
import ghidra.util.task.TaskMonitor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@FileSystemInfo(type = "fv", description = "UEFI Firmware Volume", factory = GFileSystemBaseFactory.class)
public class UEFIFirmwareVolumeFileSystem extends GFileSystemBase {
	private long offset = 0;
	private HashMap<GFile, UEFIFirmwareVolumeHeader> map;

	public UEFIFirmwareVolumeFileSystem(String fileSystemName, ByteProvider provider) {
		super(fileSystemName, provider);
		offset = 0;
		map = new HashMap<>();
	}

	@Override
	public boolean isValid(TaskMonitor monitor) throws IOException {
		long remainingLength = provider.length();
		while (remainingLength >= UEFIFirmwareVolumeConstants.UEFI_FV_SIGNATURE.length()) {
			String signature = new String(provider.readBytes(offset, 4));
			if (signature.equals(UEFIFirmwareVolumeConstants.UEFI_FV_SIGNATURE)) {
				if (remainingLength <= UEFIFirmwareVolumeConstants.UEFI_FV_HEADER_SIZE - 40) {
					return false;
				}

				if (offset - 40 >= 0) {
					Msg.debug(this, String.format("Found _FVH signature at 0x%X", offset));
					return true;
				}
			}

			offset += 4;
			remainingLength -= 4;
		}

		return false;
	}

	@Override
	public void open(TaskMonitor monitor) throws IOException {
		BinaryReader reader = new BinaryReader(provider, true);
		long index = offset - 40;
		reader.setPointerIndex(index);
		int volumeNum = 0;
		while (reader.length() - index > 0) {
			try {
				// Add each firmware volume as a directory.
				UEFIFirmwareVolumeHeader header = new UEFIFirmwareVolumeHeader(reader);
				index += header.length();
				reader.setPointerIndex(index);
				String fileName = String.format("Volume %02d - %s",
						volumeNum++, UUIDUtils.getName(header.getGUID()));
				GFile file = GFileImpl.fromPathString(this, root, fileName, null, true,
						header.length());
				map.put(file, header);

				// TODO: Add files embedded within the firmware volume
			} catch (IOException e) {
				break;
			}
		}
	}

	@Override
	public void close() throws IOException {
		super.close();
		map.clear();
	}

	@Override
	protected InputStream getData(GFile file, TaskMonitor monitor) {
		return null;
	}

	@Override
	public String getInfo(GFile file, TaskMonitor monitor) {
		Object fvFile = map.get(file);
		return fvFile.toString();
	}

	@Override
	public List<GFile> getListing(GFile directory) {
		if (directory == null || directory.equals(root)) {
			ArrayList<GFile> roots = new ArrayList<>();
			for (GFile file : map.keySet()) {
				if (file.getParentFile() == root || file.getParentFile().equals(root)) {
					roots.add(file);
				}
			}

			return roots;
		}

		ArrayList<GFile> tmp = new ArrayList<>();
		for (GFile file : map.keySet()) {
			if (file.getParentFile() == null) {
				continue;
			}

			if (file.getParentFile().equals(directory)) {
				tmp.add(file);
			}
		}

		return tmp;
	}
}