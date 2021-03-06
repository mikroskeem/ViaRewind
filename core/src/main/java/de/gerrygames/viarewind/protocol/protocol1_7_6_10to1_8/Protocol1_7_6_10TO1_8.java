package de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8;

import com.google.common.base.Charsets;
import de.gerrygames.viarewind.ViaRewind;
import de.gerrygames.viarewind.netty.EmptyChannelHandler;
import de.gerrygames.viarewind.netty.ForwardMessageToByteEncoder;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.chunks.ChunkPacketTransformer;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.entityreplacements.ArmorStandReplacement;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.entityreplacements.EndermiteReplacement;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.entityreplacements.GuardianReplacement;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.items.ItemRewriter;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.items.ReplacementRegistry1_7_6_10to1_8;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.metadata.MetadataRewriter;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.provider.TitleRenderProvider;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.storage.CompressionSendStorage;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.storage.EntityTracker;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.storage.GameProfileStorage;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.storage.PlayerAbilities;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.storage.PlayerPosition;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.storage.Scoreboard;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.storage.Windows;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.storage.WorldBorder;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.types.CustomIntType;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.types.Particle;
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.types.Types1_7_6_10;
import de.gerrygames.viarewind.replacement.EntityReplacement;
import de.gerrygames.viarewind.storage.BlockState;
import de.gerrygames.viarewind.types.VarLongType;
import de.gerrygames.viarewind.utils.ChatUtil;
import de.gerrygames.viarewind.utils.PacketUtil;
import de.gerrygames.viarewind.utils.Ticker;
import de.gerrygames.viarewind.utils.Utils;
import de.gerrygames.viarewind.utils.math.AABB;
import de.gerrygames.viarewind.utils.math.Ray3d;
import de.gerrygames.viarewind.utils.math.RayTracing;
import de.gerrygames.viarewind.utils.math.Vector3d;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import net.md_5.bungee.api.ChatColor;
import us.myles.ViaVersion.api.PacketWrapper;
import us.myles.ViaVersion.api.Via;
import us.myles.ViaVersion.api.data.UserConnection;
import us.myles.ViaVersion.api.entities.Entity1_10Types;
import us.myles.ViaVersion.api.minecraft.Position;
import us.myles.ViaVersion.api.minecraft.item.Item;
import us.myles.ViaVersion.api.minecraft.metadata.Metadata;
import us.myles.ViaVersion.api.protocol.Protocol;
import us.myles.ViaVersion.api.remapper.PacketHandler;
import us.myles.ViaVersion.api.remapper.PacketRemapper;
import us.myles.ViaVersion.api.remapper.ValueCreator;
import us.myles.ViaVersion.api.type.Type;
import us.myles.ViaVersion.api.type.types.CustomByteType;
import us.myles.ViaVersion.api.type.types.version.Types1_8;
import us.myles.ViaVersion.exception.CancelException;
import us.myles.ViaVersion.packets.Direction;
import us.myles.ViaVersion.packets.State;
import us.myles.ViaVersion.protocols.base.ProtocolInfo;
import us.myles.ViaVersion.protocols.protocol1_9_3to1_9_1_2.storage.ClientWorld;
import us.myles.ViaVersion.protocols.protocol1_9to1_8.storage.ClientChunks;
import us.myles.viaversion.libs.opennbt.tag.builtin.CompoundTag;
import us.myles.viaversion.libs.opennbt.tag.builtin.ListTag;
import us.myles.viaversion.libs.opennbt.tag.builtin.StringTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Protocol1_7_6_10TO1_8 extends Protocol {

	@Override
	protected void registerPackets() {
		//Keep Alive
		this.registerOutgoing(State.PLAY, 0x00, 0x00, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);
			}
		});

		//Join Game
		this.registerOutgoing(State.PLAY, 0x01, 0x01, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.INT);  //Entiy Id
				map(Type.UNSIGNED_BYTE);  //Gamemode
				map(Type.BYTE);  //Dimension
				map(Type.UNSIGNED_BYTE);  //Difficulty
				map(Type.UNSIGNED_BYTE);  //Max players
				map(Type.STRING);  //Level Type
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						if (!ViaRewind.getConfig().isReplaceAdventureMode()) return;
						if (packetWrapper.get(Type.UNSIGNED_BYTE, 0)==2) {
							packetWrapper.set(Type.UNSIGNED_BYTE, 0, (short) 0);
						}
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.read(Type.BOOLEAN);  //Reduced Debug Info

						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						tracker.setGamemode(packetWrapper.get(Type.UNSIGNED_BYTE, 0));
						tracker.setPlayerId(packetWrapper.get(Type.INT, 0));
						tracker.getClientEntityTypes().put(tracker.getPlayerId(), Entity1_10Types.EntityType.ENTITY_HUMAN);
						tracker.setDimension(packetWrapper.get(Type.BYTE, 0));
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						ClientWorld world = packetWrapper.user().get(ClientWorld.class);
						world.setEnvironment(packetWrapper.get(Type.BYTE, 0));
					}
				});
			}
		});

		//Chat Message
		this.registerOutgoing(State.PLAY, 0x02, 0x02, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.STRING);  //Chat Message
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int position = packetWrapper.read(Type.BYTE);
						if (position==2) packetWrapper.cancel();
					}
				});
			}
		});

		//Entity Equipment
		this.registerOutgoing(State.PLAY, 0x04, 0x04, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);  //Entity Id
				map(Type.SHORT);  //Slot
				map(Type.ITEM, Types1_7_6_10.COMPRESSED_NBT_ITEM);  //Item
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Item item = packetWrapper.get(Types1_7_6_10.COMPRESSED_NBT_ITEM, 0);
						ItemRewriter.toClient(item);
						packetWrapper.set(Types1_7_6_10.COMPRESSED_NBT_ITEM, 0, item);
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						if (packetWrapper.get(Type.SHORT, 0)>4) packetWrapper.cancel();
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						if (packetWrapper.isCancelled()) return;
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						UUID uuid = tracker.getPlayerUUID(packetWrapper.get(Type.INT, 0));
						if (uuid==null) return;
						Item[] equipment = tracker.getPlayerEquipment(uuid);
						if (equipment==null) tracker.setPlayerEquipment(uuid, equipment = new Item[5]);
						equipment[packetWrapper.get(Type.SHORT, 0)] = packetWrapper.get(Types1_7_6_10.COMPRESSED_NBT_ITEM, 0);
						GameProfileStorage storage = packetWrapper.user().get(GameProfileStorage.class);
						GameProfileStorage.GameProfile profile = storage.get(uuid);
						if (profile!=null && profile.gamemode==3) packetWrapper.cancel();
					}
				});
			}
		});

		//Spawn Position
		this.registerOutgoing(State.PLAY, 0x05, 0x05,  new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Position position = packetWrapper.read(Type.POSITION);
						packetWrapper.write(Type.INT, position.getX().intValue());
						packetWrapper.write(Type.INT, position.getY().intValue());
						packetWrapper.write(Type.INT, position.getZ().intValue());
					}
				});
			}
		});

		//Update Health
		this.registerOutgoing(State.PLAY, 0x06, 0x06, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.FLOAT);  //Health
				map(Type.VAR_INT, Type.SHORT);  //Food
				map(Type.FLOAT);  //Food Saturation
			}
		});

		//Respawn
		this.registerOutgoing(State.PLAY, 0x07, 0x07, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.INT);
				map(Type.UNSIGNED_BYTE);
				map(Type.UNSIGNED_BYTE);
				map(Type.STRING);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						if (!ViaRewind.getConfig().isReplaceAdventureMode()) return;
						if (packetWrapper.get(Type.UNSIGNED_BYTE, 1)==2) {
							packetWrapper.set(Type.UNSIGNED_BYTE, 1, (short) 0);
						}
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						tracker.setGamemode(packetWrapper.get(Type.UNSIGNED_BYTE, 1));
						if (tracker.getDimension()!=packetWrapper.get(Type.INT, 0)) {
							tracker.setDimension(packetWrapper.get(Type.INT, 0));
							tracker.clearEntities();
						}
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						ClientWorld world = packetWrapper.user().get(ClientWorld.class);
						world.setEnvironment(packetWrapper.get(Type.INT, 0));
					}
				});
			}
		});

		//Player Position And Look
		this.registerOutgoing(State.PLAY, 0x08, 0x08, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.DOUBLE);  //x
				map(Type.DOUBLE);  //y
				map(Type.DOUBLE);  //z
				map(Type.FLOAT);  //yaw
				map(Type.FLOAT);  //pitch
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						PlayerPosition playerPosition = packetWrapper.user().get(PlayerPosition.class);
						playerPosition.setPositionPacketReceived(true);

						int flags = packetWrapper.read(Type.BYTE);
						if ((flags & 0x01) == 0x01) {
							double x = packetWrapper.get(Type.DOUBLE, 0);
							x += playerPosition.getPosX();
							packetWrapper.set(Type.DOUBLE, 0, x);
						}
						double y = packetWrapper.get(Type.DOUBLE, 1);
						if ((flags & 0x02) == 0x02) {
							y += playerPosition.getPosY();
						}
						playerPosition.setReceivedPosY(y);
						y += 1.621;
						packetWrapper.set(Type.DOUBLE, 1, y);
						if ((flags & 0x04) == 0x04) {
							double z = packetWrapper.get(Type.DOUBLE, 2);
							z += playerPosition.getPosZ();
							packetWrapper.set(Type.DOUBLE, 2, z);
						}
						if ((flags & 0x08) == 0x08) {
							float yaw = packetWrapper.get(Type.FLOAT, 0);
							yaw += playerPosition.getYaw();
							packetWrapper.set(Type.FLOAT, 0, yaw);
						}
						if ((flags & 0x10) == 0x10) {
							float pitch = packetWrapper.get(Type.FLOAT, 1);
							pitch += playerPosition.getPitch();
							packetWrapper.set(Type.FLOAT, 1, pitch);
						}
					}
				});
				create(new ValueCreator() {
					@Override
					public void write(PacketWrapper packetWrapper) throws Exception {
						PlayerPosition playerPosition = packetWrapper.user().get(PlayerPosition.class);
						packetWrapper.write(Type.BOOLEAN, playerPosition.isOnGround());
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						if (tracker.getSpectating()!=tracker.getPlayerId()) {
							packetWrapper.cancel();
						}
					}
				});
			}
		});

		//Use Bed
		this.registerOutgoing(State.PLAY, 0x0A, 0x0A, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);  //Entity Id
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Position position = packetWrapper.read(Type.POSITION);
						packetWrapper.write(Type.INT, position.getX().intValue());
						packetWrapper.write(Type.UNSIGNED_BYTE, position.getY().shortValue());
						packetWrapper.write(Type.INT, position.getZ().intValue());
					}
				});
			}
		});

		//Spawn Player
		this.registerOutgoing(State.PLAY, 0x0C, 0x0C, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						UUID uuid = packetWrapper.read(Type.UUID);
						packetWrapper.write(Type.STRING, uuid.toString());

						GameProfileStorage gameProfileStorage = packetWrapper.user().get(GameProfileStorage.class);

						GameProfileStorage.GameProfile gameProfile = gameProfileStorage.get(uuid);
						if (gameProfile==null) {
							packetWrapper.write(Type.STRING, "");
							packetWrapper.write(Type.VAR_INT, 0);
						} else {
							packetWrapper.write(Type.STRING, gameProfile.name.length()>16 ? gameProfile.name.substring(0, 16) : gameProfile.name);
							packetWrapper.write(Type.VAR_INT, gameProfile.properties.size());
							for (GameProfileStorage.Property property : gameProfile.properties) {
								packetWrapper.write(Type.STRING, property.name);
								packetWrapper.write(Type.STRING, property.value);
								packetWrapper.write(Type.STRING, property.signature==null ? "" : property.signature);
							}
						}

						if (gameProfile!=null && gameProfile.gamemode==3) {
							int entityId = packetWrapper.get(Type.VAR_INT, 0);

							PacketWrapper equipmentPacket = new PacketWrapper(0x04, null, packetWrapper.user());
							equipmentPacket.write(Type.INT, entityId);
							equipmentPacket.write(Type.SHORT, (short) 4);
							equipmentPacket.write(Types1_7_6_10.COMPRESSED_NBT_ITEM, gameProfile.getSkull());

							PacketUtil.sendPacket(equipmentPacket, Protocol1_7_6_10TO1_8.class);

							for (short i = 0; i<4; i++) {
								equipmentPacket = new PacketWrapper(0x04, null, packetWrapper.user());
								equipmentPacket.write(Type.INT, entityId);
								equipmentPacket.write(Type.SHORT, i);
								equipmentPacket.write(Types1_7_6_10.COMPRESSED_NBT_ITEM, null);
								PacketUtil.sendPacket(equipmentPacket, Protocol1_7_6_10TO1_8.class);
							}
						}

						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						tracker.addPlayer(packetWrapper.get(Type.VAR_INT, 0), uuid);
					}
				});
				map(Type.INT);
				map(Type.INT);
				map(Type.INT);
				map(Type.BYTE);
				map(Type.BYTE);
				map(Type.SHORT);
				map(Types1_8.METADATA_LIST, Types1_7_6_10.METADATA_LIST);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						List<Metadata> metadata = packetWrapper.get(Types1_7_6_10.METADATA_LIST, 0);  //Metadata
						MetadataRewriter.transform(Entity1_10Types.EntityType.PLAYER, metadata);
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.VAR_INT, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						tracker.getClientEntityTypes().put(entityId, Entity1_10Types.EntityType.PLAYER);
						tracker.sendMetadataBuffer(entityId);
					}
				});
			}
		});

		//Collect Item
		this.registerOutgoing(State.PLAY, 0x0D, 0x0D, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);  //Collected Entity ID
				map(Type.VAR_INT, Type.INT);  //Collector Entity ID
			}
		});

		//Spawn Object
		this.registerOutgoing(State.PLAY, 0x0E, 0x0E, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT);
				map(Type.BYTE);
				map(Type.INT);
				map(Type.INT);
				map(Type.INT);
				map(Type.BYTE);
				map(Type.BYTE);
				map(Type.INT);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.VAR_INT, 0);
						byte typeId = packetWrapper.get(Type.BYTE, 0);
						int x = packetWrapper.get(Type.INT, 0);
						int y = packetWrapper.get(Type.INT, 1);
						int z = packetWrapper.get(Type.INT, 2);
						byte pitch = packetWrapper.get(Type.BYTE, 1);
						byte yaw = packetWrapper.get(Type.BYTE, 2);

						if (typeId == 71) {
							switch (yaw) {
								case -128:
									z += 32;
									yaw = 0;
									break;
								case -64:
									x -= 32;
									yaw = -64;
									break;
								case 0:
									z -= 32;
									yaw = -128;
									break;
								case 64:
									x += 32;
									yaw = 64;
									break;
							}
						} else if (typeId == 78) {
							packetWrapper.cancel();
							EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
							ArmorStandReplacement armorStand = new ArmorStandReplacement(entityId, packetWrapper.user());
							armorStand.setLocation(x / 32.0, y / 32.0, z / 32.0);
							armorStand.setYawPitch(yaw * 360f / 256, pitch * 360f / 256);
							armorStand.setHeadYaw(yaw * 360f / 256);
							tracker.addEntityReplacement(armorStand);
						} else if (typeId == 10) {
							y += 12;
						}

						packetWrapper.set(Type.BYTE, 0, typeId);
						packetWrapper.set(Type.INT, 0, x);
						packetWrapper.set(Type.INT, 1, y);
						packetWrapper.set(Type.INT, 2, z);
						packetWrapper.set(Type.BYTE, 1, pitch);
						packetWrapper.set(Type.BYTE, 2, yaw);
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(final PacketWrapper packetWrapper) throws Exception {
						final int entityId = packetWrapper.get(Type.VAR_INT, 0);
						final int typeId = packetWrapper.get(Type.BYTE, 0);
						final EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						final Entity1_10Types.EntityType type = Entity1_10Types.getTypeFromId(typeId, true);
						tracker.getClientEntityTypes().put(entityId, type);
						tracker.sendMetadataBuffer(entityId);
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						if (packetWrapper.isCancelled()) return;
						int data = packetWrapper.get(Type.INT, 3);
						if (data>0) {
							packetWrapper.passthrough(Type.SHORT);
							packetWrapper.passthrough(Type.SHORT);
							packetWrapper.passthrough(Type.SHORT);
						}
					}
				});
			}
		});

		//Spawn Mob
		this.registerOutgoing(State.PLAY, 0x0F, 0x0F, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT);
				map(Type.UNSIGNED_BYTE);
				map(Type.INT);
				map(Type.INT);
				map(Type.INT);
				map(Type.BYTE);
				map(Type.BYTE);
				map(Type.BYTE);
				map(Type.SHORT);
				map(Type.SHORT);
				map(Type.SHORT);
				map(Types1_8.METADATA_LIST, Types1_7_6_10.METADATA_LIST);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.VAR_INT, 0);
						int typeId = packetWrapper.get(Type.UNSIGNED_BYTE, 0);
						int x = packetWrapper.get(Type.INT, 0);
						int y = packetWrapper.get(Type.INT, 1);
						int z = packetWrapper.get(Type.INT, 2);
						byte pitch = packetWrapper.get(Type.BYTE, 1);
						byte yaw = packetWrapper.get(Type.BYTE, 0);
						byte headYaw = packetWrapper.get(Type.BYTE, 2);

						if (typeId==78) {
							packetWrapper.cancel();

							EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
							ArmorStandReplacement armorStand = new ArmorStandReplacement(entityId, packetWrapper.user());
							armorStand.setLocation(x / 32.0, y / 32.0, z / 32.0);
							armorStand.setYawPitch(yaw * 360f / 256, pitch * 360f / 256);
							armorStand.setHeadYaw(headYaw * 360f / 256);
							tracker.addEntityReplacement(armorStand);
						} else if (typeId==68) {
							packetWrapper.cancel();

							EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
							GuardianReplacement guardian = new GuardianReplacement(entityId, packetWrapper.user());
							guardian.setLocation(x / 32.0, y / 32.0, z / 32.0);
							guardian.setYawPitch(yaw * 360f / 256, pitch * 360f / 256);
							guardian.setHeadYaw(headYaw * 360f / 256);
							tracker.addEntityReplacement(guardian);
						} else if (typeId==67) {
							packetWrapper.cancel();

							EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
							EndermiteReplacement endermite = new EndermiteReplacement(entityId, packetWrapper.user());
							endermite.setLocation(x / 32.0, y / 32.0, z / 32.0);
							endermite.setYawPitch(yaw * 360f / 256, pitch * 360f / 256);
							endermite.setHeadYaw(headYaw * 360f / 256);
							tracker.addEntityReplacement(endermite);
						} else if (typeId==101 || typeId==30 || typeId==255 || typeId==-1) {
							packetWrapper.cancel();
						}
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.VAR_INT, 0);
						int typeId = packetWrapper.get(Type.UNSIGNED_BYTE, 0) & 0xff;
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						tracker.getClientEntityTypes().put(entityId, Entity1_10Types.getTypeFromId(typeId, false));
						tracker.sendMetadataBuffer(entityId);
					}
				});
				handler(new PacketHandler() {
					public void handle(PacketWrapper wrapper) throws Exception {
						List<Metadata> metadataList = wrapper.get(Types1_7_6_10.METADATA_LIST, 0);
						int entityId = wrapper.get(Type.VAR_INT, 0);
						EntityTracker tracker = wrapper.user().get(EntityTracker.class);
						if (tracker.getEntityReplacement(entityId)!=null) {
							tracker.getEntityReplacement(entityId).updateMetadata(metadataList);
						} else if (tracker.getClientEntityTypes().containsKey(entityId)) {
							MetadataRewriter.transform(tracker.getClientEntityTypes().get(entityId), metadataList);
						} else {
							wrapper.cancel();
						}
					}
				});

			}
		});

		//Spawn Painting
		this.registerOutgoing(State.PLAY, 0x10, 0x10, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT);  //Entity Id
				map(Type.STRING);  //Title
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Position position = packetWrapper.read(Type.POSITION);
						packetWrapper.write(Type.INT, position.getX().intValue());
						packetWrapper.write(Type.INT, position.getY().intValue());
						packetWrapper.write(Type.INT, position.getZ().intValue());
					}
				});
				map(Type.UNSIGNED_BYTE, Type.INT);  //Rotation
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.VAR_INT, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						tracker.getClientEntityTypes().put(entityId, Entity1_10Types.EntityType.PAINTING);
						tracker.sendMetadataBuffer(entityId);
					}
				});
			}
		});

		//Spawn Experience Orb
		this.registerOutgoing(State.PLAY, 0x11, 0x11, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT);
				map(Type.INT);
				map(Type.INT);
				map(Type.INT);
				map(Type.SHORT);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.VAR_INT, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						tracker.getClientEntityTypes().put(entityId, Entity1_10Types.EntityType.EXPERIENCE_ORB);
						tracker.sendMetadataBuffer(entityId);
					}
				});
			}
		});

		//Entity Velocity
		this.registerOutgoing(State.PLAY, 0x12, 0x12, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);  //Entity Id
				map(Type.SHORT);  //velX
				map(Type.SHORT);  //velY
				map(Type.SHORT);  //velZ
			}
		});

		//Destroy Entities
		this.registerOutgoing(State.PLAY, 0x13, 0x13, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Integer[] entityIds = packetWrapper.read(Type.VAR_INT_ARRAY);

						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						for (int entityId : entityIds) tracker.removeEntity(entityId);

						while (entityIds.length > 127) {
							Integer[] entityIds2 = new Integer[127];
							System.arraycopy(entityIds, 0, entityIds2, 0, 127);
							Integer[] temp = new Integer[entityIds.length-127];
							System.arraycopy(entityIds, 127, temp, 0, temp.length);
							entityIds = temp;

							PacketWrapper destroy = new PacketWrapper(0x13, null, packetWrapper.user());
							destroy.write(Type.BYTE, (byte)127);
							CustomIntType customIntType = new CustomIntType(127);
							destroy.write(customIntType, entityIds2);
							PacketUtil.sendPacket(destroy, Protocol1_7_6_10TO1_8.class);
						}

						packetWrapper.write(Type.BYTE, ((Integer)entityIds.length).byteValue());

						CustomIntType customIntType = new CustomIntType(entityIds.length);
						packetWrapper.write(customIntType, entityIds);
					}
				});  //Entity Id Array
			}
		});

		//Entity
		this.registerOutgoing(State.PLAY, 0x14, 0x14, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);  //Entity Id
			}
		});

		//Entity Relative Move
		this.registerOutgoing(State.PLAY, 0x15, 0x15, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);  //Entity Id
				map(Type.BYTE);  //x
				map(Type.BYTE);  //y
				map(Type.BYTE);  //z
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.read(Type.BOOLEAN);
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.INT, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						EntityReplacement replacement = tracker.getEntityReplacement(entityId);
						if (replacement!=null) {
							packetWrapper.cancel();
							int x = packetWrapper.get(Type.BYTE, 0);
							int y = packetWrapper.get(Type.BYTE, 1);
							int z = packetWrapper.get(Type.BYTE, 2);
							replacement.relMove(x / 32.0, y / 32.0, z / 32.0);
						}
					}
				});
			}
		});

		//Entity Look
		this.registerOutgoing(State.PLAY, 0x16, 0x16, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);  //Entity Id
				map(Type.BYTE);  //yaw
				map(Type.BYTE);  //pitch
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.read(Type.BOOLEAN);
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.INT, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						EntityReplacement replacement = tracker.getEntityReplacement(entityId);
						if (replacement!=null) {
							packetWrapper.cancel();
							int yaw = packetWrapper.get(Type.BYTE, 0);
							int pitch = packetWrapper.get(Type.BYTE, 1);
							replacement.setYawPitch(yaw * 360f / 256, pitch * 360f / 256);
						}
					}
				});
			}
		});

		//Entity Look and Relative Move
		this.registerOutgoing(State.PLAY, 0x17, 0x17, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);  //Entity Id
				map(Type.BYTE);  //x
				map(Type.BYTE);  //y
				map(Type.BYTE);  //z
				map(Type.BYTE);  //yaw
				map(Type.BYTE);  //pitch
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.read(Type.BOOLEAN);
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.INT, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						EntityReplacement replacement = tracker.getEntityReplacement(entityId);
						if (replacement!=null) {
							packetWrapper.cancel();
							int x = packetWrapper.get(Type.BYTE, 0);
							int y = packetWrapper.get(Type.BYTE, 1);
							int z = packetWrapper.get(Type.BYTE, 2);
							int yaw = packetWrapper.get(Type.BYTE, 3);
							int pitch = packetWrapper.get(Type.BYTE, 4);
							replacement.relMove(x / 32.0, y / 32.0, z / 32.0);
							replacement.setYawPitch(yaw * 360f / 256, pitch * 360f / 256);
						}
					}
				});
			}
		});

		//Entity Teleport
		this.registerOutgoing(State.PLAY, 0x18, 0x18, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);  //Entity Id
				map(Type.INT);  //x
				map(Type.INT);  //y
				map(Type.INT);  //z
				map(Type.BYTE);  //yaw
				map(Type.BYTE);  //pitch
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.read(Type.BOOLEAN);
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.INT, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						Entity1_10Types.EntityType type = tracker.getClientEntityTypes().get(entityId);
						if (type == Entity1_10Types.EntityType.MINECART_ABSTRACT) {
							int y = packetWrapper.get(Type.INT, 2);
							y += 12;
							packetWrapper.set(Type.INT, 2, y);
						}
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.INT, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						EntityReplacement replacement = tracker.getEntityReplacement(entityId);
						if (replacement!=null) {
							packetWrapper.cancel();
							int x = packetWrapper.get(Type.INT, 1);
							int y = packetWrapper.get(Type.INT, 2);
							int z = packetWrapper.get(Type.INT, 3);
							int yaw = packetWrapper.get(Type.BYTE, 0);
							int pitch = packetWrapper.get(Type.BYTE, 1);
							replacement.setLocation(x / 32.0, y / 32.0, z / 32.0);
							replacement.setYawPitch(yaw * 360f / 256, pitch * 360f / 256);
						}
					}
				});
			}
		});

		//Entity Head Look
		this.registerOutgoing(State.PLAY, 0x19, 0x19, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);  //Entity Id
				map(Type.BYTE);  //Head yaw
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.INT, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						EntityReplacement replacement = tracker.getEntityReplacement(entityId);
						if (replacement!=null) {
							packetWrapper.cancel();
							int yaw = packetWrapper.get(Type.BYTE, 0);
							replacement.setHeadYaw(yaw * 360f / 256);
						}
					}
				});
			}
		});

		//Attach Entity
		this.registerOutgoing(State.PLAY, 0x1B, 0x1B, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.INT);
				map(Type.INT);
				map(Type.BOOLEAN);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						boolean leash = packetWrapper.get(Type.BOOLEAN, 0);
						if (leash) return;
						int passenger = packetWrapper.get(Type.INT, 0);
						int vehicle = packetWrapper.get(Type.INT, 1);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						tracker.setPassenger(vehicle, passenger);
					}
				});
			}
		});

		//Entity Metadata
		this.registerOutgoing(State.PLAY, 0x1C, 0x1C, new PacketRemapper() {
			@Override
			public void registerMap() {
				//TODO sneak/unsneak
				map(Type.VAR_INT, Type.INT);  //Entity Id
				map(Types1_8.METADATA_LIST, Types1_7_6_10.METADATA_LIST);  //Metadata
				handler(new PacketHandler() {
					public void handle(PacketWrapper wrapper) throws Exception {
						List<Metadata> metadataList = wrapper.get(Types1_7_6_10.METADATA_LIST, 0);
						int entityId = wrapper.get(Type.INT, 0);
						EntityTracker tracker = wrapper.user().get(EntityTracker.class);
						if (tracker.getClientEntityTypes().containsKey(entityId)) {
							EntityReplacement replacement = tracker.getEntityReplacement(entityId);
							if (replacement!=null) {
								wrapper.cancel();
								replacement.updateMetadata(metadataList);
							} else {
								MetadataRewriter.transform(tracker.getClientEntityTypes().get(entityId), metadataList);
								if (metadataList.isEmpty()) wrapper.cancel();
							}
						} else {
							tracker.addMetadataToBuffer(entityId, metadataList);
							wrapper.cancel();
						}
					}
				});
			}
		});

		//Entity Effect
		this.registerOutgoing(State.PLAY, 0x1D, 0x1D, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);  //Entity Id
				map(Type.BYTE);  //Effect Id
				map(Type.BYTE);  //Amplifier
				map(Type.VAR_INT, Type.SHORT);  //Duration
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.read(Type.BYTE);  //Hide Particles
					}
				});
			}
		});

		//Remove Entity Effect
		this.registerOutgoing(State.PLAY, 0x1E, 0x1E, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);  //Entity Id
				map(Type.BYTE);  //Effect Id
			}
		});

		//Set Experience
		this.registerOutgoing(State.PLAY, 0x1F, 0x1F, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.FLOAT);  //Experience bar
				map(Type.VAR_INT, Type.SHORT);  //Level
				map(Type.VAR_INT, Type.SHORT);  //Total Experience
			}
		});

		//Entity Properties
		this.registerOutgoing(State.PLAY, 0x20, 0x20, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT, Type.INT);  //Entity Id
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.INT, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						if (tracker.getEntityReplacement(entityId)!=null) {
							packetWrapper.cancel();
							return;
						}
						int amount = packetWrapper.passthrough(Type.INT);
						for (int i = 0; i<amount; i++) {
							packetWrapper.passthrough(Type.STRING);
							packetWrapper.passthrough(Type.DOUBLE);
							int modifierlength = packetWrapper.read(Type.VAR_INT);
							packetWrapper.write(Type.SHORT, (short)modifierlength);
							for (int j = 0; j<modifierlength; j++) {
								packetWrapper.passthrough(Type.UUID);
								packetWrapper.passthrough(Type.DOUBLE);
								packetWrapper.passthrough(Type.BYTE);
							}
						}
					}
				});

			}
		});

		//Chunk Data
		this.registerOutgoing(State.PLAY, 0x21, 0x21, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						ChunkPacketTransformer.transformChunk(packetWrapper);
					}
				});
			}
		});

		//Multi Block Change
		this.registerOutgoing(State.PLAY, 0x22, 0x22, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						ChunkPacketTransformer.transformMultiBlockChange(packetWrapper);
					}
				});
			}
		});

		//Block Change
		this.registerOutgoing(State.PLAY, 0x23, 0x23, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Position position = packetWrapper.read(Type.POSITION);
						packetWrapper.write(Type.INT, position.getX().intValue());
						packetWrapper.write(Type.UNSIGNED_BYTE, position.getY().shortValue());
						packetWrapper.write(Type.INT, position.getZ().intValue());
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int data = packetWrapper.read(Type.VAR_INT);

						int blockId = data >> 4;
						int meta = data & 0xF;

						BlockState state = ReplacementRegistry1_7_6_10to1_8.replace(new BlockState(blockId, meta));

						blockId = state.getId();
						meta = state.getData();

						packetWrapper.write(Type.VAR_INT, blockId);
						packetWrapper.write(Type.UNSIGNED_BYTE, (short)meta);
					}
				});  //Block Data
			}
		});

		//Block Action
		this.registerOutgoing(State.PLAY, 0x24, 0x24, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Position position = packetWrapper.read(Type.POSITION);
						packetWrapper.write(Type.INT, position.getX().intValue());
						packetWrapper.write(Type.SHORT, position.getY().shortValue());
						packetWrapper.write(Type.INT, position.getZ().intValue());
					}
				});
				map(Type.UNSIGNED_BYTE);
				map(Type.UNSIGNED_BYTE);
				map(Type.VAR_INT);
			}
		});

		//Block Break Animation
		this.registerOutgoing(State.PLAY, 0x25, 0x25, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT);  //Entity Id
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Position position = packetWrapper.read(Type.POSITION);
						packetWrapper.write(Type.INT, position.getX().intValue());
						packetWrapper.write(Type.INT, position.getY().intValue());
						packetWrapper.write(Type.INT, position.getZ().intValue());
					}
				});
				map(Type.BYTE);  //Progress
			}
		});

		//Map Chunk Bulk
		this.registerOutgoing(State.PLAY, 0x26, 0x26, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						ChunkPacketTransformer.transformChunkBulk(packetWrapper);
					}
				});
			}
		});

		//Effect
		this.registerOutgoing(State.PLAY, 0x28, 0x28, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.INT);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Position position = packetWrapper.read(Type.POSITION);
						packetWrapper.write(Type.INT, position.getX().intValue());
						packetWrapper.write(Type.BYTE, position.getY().byteValue());
						packetWrapper.write(Type.INT, position.getZ().intValue());
					}
				});
				map(Type.INT);
				map(Type.BOOLEAN);
			}
		});

		//Particle
		this.registerOutgoing(State.PLAY, 0x2A, 0x2A, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int particleId = packetWrapper.read(Type.INT);
						Particle particle = Particle.find(particleId);
						if (particle == null) particle = Particle.CRIT;
						packetWrapper.write(Type.STRING, particle.name);

						packetWrapper.read(Type.BOOLEAN);
					}
				});
				map(Type.FLOAT);
				map(Type.FLOAT);
				map(Type.FLOAT);
				map(Type.FLOAT);
				map(Type.FLOAT);
				map(Type.FLOAT);
				map(Type.FLOAT);
				map(Type.INT);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						String name = packetWrapper.get(Type.STRING, 0);
						Particle particle = Particle.find(name);

						if (particle==Particle.ICON_CRACK || particle==Particle.BLOCK_CRACK || particle==Particle.BLOCK_DUST) {
							int id = packetWrapper.read(Type.VAR_INT);
							int data = particle==Particle.ICON_CRACK ? packetWrapper.read(Type.VAR_INT) : 0;
							if (id>=256 && id<=422 || id>=2256 && id<=2267) {  //item
								particle = Particle.ICON_CRACK;
							} else if (id>=0 && id<=164 || id>=170 && id<=175) {
								if (particle==Particle.ICON_CRACK) particle = Particle.BLOCK_CRACK;
							} else {
								packetWrapper.cancel();
								return;
							}
							name = particle.name + "_" + id + "_" + data;
						}

						packetWrapper.set(Type.STRING, 0, name);
					}
				});
			}
		});

		//Change Game State
		this.registerOutgoing(State.PLAY, 0x2B, 0x2B, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.UNSIGNED_BYTE);
				map(Type.FLOAT);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int mode = packetWrapper.get(Type.UNSIGNED_BYTE, 0);
						if (mode!=3) return;
						int gamemode = packetWrapper.get(Type.FLOAT, 0).intValue();
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						if (gamemode==3 || tracker.getGamemode()==3) {
							UUID uuid = packetWrapper.user().get(ProtocolInfo.class).getUuid();
							Item[] equipment;
							if (gamemode==3) {
								GameProfileStorage.GameProfile profile = packetWrapper.user().get(GameProfileStorage.class).get(uuid);
								equipment = new Item[5];
								equipment[4] = profile.getSkull();
							} else {
								equipment = tracker.getPlayerEquipment(uuid);
								if (equipment==null) equipment = new Item[5];
							}

							for (int i = 1; i<5; i++) {
								PacketWrapper setSlot = new PacketWrapper(0x2F, null, packetWrapper.user());
								setSlot.write(Type.BYTE, (byte) 0);
								setSlot.write(Type.SHORT, (short) (9 - i));
								setSlot.write(Types1_7_6_10.COMPRESSED_NBT_ITEM, equipment[i]);
								PacketUtil.sendPacket(setSlot, Protocol1_7_6_10TO1_8.class);
							}
						}
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int mode = packetWrapper.get(Type.UNSIGNED_BYTE, 0);
						if (mode==3) {
							int gamemode = packetWrapper.get(Type.FLOAT, 0).intValue();
							if (gamemode==2 && ViaRewind.getConfig().isReplaceAdventureMode()) {
								gamemode = 0;
								packetWrapper.set(Type.FLOAT, 0, 0.0f);
							}
							packetWrapper.user().get(EntityTracker.class).setGamemode(gamemode);
						}
					}
				});
			}
		});

		//Spawn Global Entity
		this.registerOutgoing(State.PLAY, 0x2C, 0x2C, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.VAR_INT);
				map(Type.BYTE);
				map(Type.INT);
				map(Type.INT);
				map(Type.INT);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.get(Type.VAR_INT, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						tracker.getClientEntityTypes().put(entityId, Entity1_10Types.EntityType.LIGHTNING);
						tracker.sendMetadataBuffer(entityId);
					}
				});
			}
		});

		//Open Window
		this.registerOutgoing(State.PLAY, 0x2D, 0x2D, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						short windowId = packetWrapper.passthrough(Type.UNSIGNED_BYTE);
						String windowType = packetWrapper.read(Type.STRING);
						short windowtypeId = (short) Windows.getInventoryType(windowType);
						packetWrapper.write(Type.UNSIGNED_BYTE, windowtypeId);
						packetWrapper.user().get(Windows.class).types.put(windowId, windowtypeId);
						String title = packetWrapper.read(Type.STRING);  //Title
						try {
							title = ChatUtil.jsonToLegacy(title);
						} catch (IllegalArgumentException ignored) {  //Bungeecord Chat API included in 1.8 is missing HoverAction SHOW_ENTITY enum .-.
							title = "";
						}
						title = ChatUtil.removeUnusedColor(title, '8');
						if (title.length() > 32) {
							title = title.substring(0, 32);
						}
						packetWrapper.write(Type.STRING, title);  //Window title
						packetWrapper.passthrough(Type.UNSIGNED_BYTE);
						packetWrapper.write(Type.BOOLEAN, true);
						if (windowtypeId == 11) packetWrapper.passthrough(Type.INT);  //Entity Id
					}
				});
			}
		});

		//Close Window
		this.registerOutgoing(State.PLAY, 0x2E, 0x2E, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.UNSIGNED_BYTE);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						short windowsId = packetWrapper.get(Type.UNSIGNED_BYTE, 0);
						packetWrapper.user().get(Windows.class).remove(windowsId);
					}
				});
			}
		});

		//Set Slot
		this.registerOutgoing(State.PLAY, 0x2F, 0x2F, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						short windowId = packetWrapper.read(Type.BYTE);  //Window Id
						short windowType = packetWrapper.user().get(Windows.class).get(windowId);
						packetWrapper.write(Type.BYTE, (byte)windowId);
						short slot = packetWrapper.read(Type.SHORT);
						if (windowType==4) {
							if (slot==1) {
								packetWrapper.cancel();
								return;
							} else if (slot>=2) {
								slot -= 1;
							}
						}
						packetWrapper.write(Type.SHORT, slot);  //Slot
					}
				});
				map(Type.ITEM, Types1_7_6_10.COMPRESSED_NBT_ITEM);  //Item
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Item item = packetWrapper.get(Types1_7_6_10.COMPRESSED_NBT_ITEM, 0);
						ItemRewriter.toClient(item);
						packetWrapper.set(Types1_7_6_10.COMPRESSED_NBT_ITEM, 0, item);
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						short windowId = packetWrapper.get(Type.BYTE, 0);
						if (windowId!=0) return;
						short slot = packetWrapper.get(Type.SHORT, 0);
						if (slot<5 || slot>8) return;
						Item item = packetWrapper.get(Types1_7_6_10.COMPRESSED_NBT_ITEM, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						UUID uuid = packetWrapper.user().get(ProtocolInfo.class).getUuid();
						Item[] equipment = tracker.getPlayerEquipment(uuid);
						if (equipment==null) {
							tracker.setPlayerEquipment(uuid, equipment = new Item[5]);
						}
						equipment[9 - slot] = item;
						if (tracker.getGamemode()==3) packetWrapper.cancel();
					}
				});
			}
		});

		//Window Items
		this.registerOutgoing(State.PLAY, 0x30, 0x30, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						short windowId = packetWrapper.read(Type.UNSIGNED_BYTE);  //Window Id
						short windowType = packetWrapper.user().get(Windows.class).get(windowId);
						packetWrapper.write(Type.UNSIGNED_BYTE, windowId);
						Item[] items = packetWrapper.read(Type.ITEM_ARRAY);
						if (windowType==4) {
							Item[] old = items;
							items = new Item[old.length - 1];
							items[0] = old[0];
							System.arraycopy(old, 2, items, 1, old.length - 3);
						}
						for (int i = 0; i<items.length; i++) items[i] = ItemRewriter.toClient(items[i]);
						packetWrapper.write(Types1_7_6_10.COMPRESSED_NBT_ITEM_ARRAY, items);  //Items
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						short windowId = packetWrapper.get(Type.UNSIGNED_BYTE, 0);
						if (windowId!=0) return;
						Item[] items = packetWrapper.get(Types1_7_6_10.COMPRESSED_NBT_ITEM_ARRAY, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						UUID uuid = packetWrapper.user().get(ProtocolInfo.class).getUuid();
						Item[] equipment = tracker.getPlayerEquipment(uuid);
						if (equipment==null) {
							tracker.setPlayerEquipment(uuid, equipment = new Item[5]);
						}
						for (int i = 5; i<9; i++) {
							equipment[9 - i] = items[i];
							if (tracker.getGamemode()==3) items[i] = null;
						}
						if (tracker.getGamemode()==3) {
							GameProfileStorage.GameProfile profile = packetWrapper.user().get(GameProfileStorage.class).get(uuid);
							if (profile!=null) items[5] = profile.getSkull();
						}
					}
				});
			}
		});

		//Window Data
		this.registerOutgoing(State.PLAY, 0x31, 0x31, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.UNSIGNED_BYTE);
				map(Type.SHORT);
				map(Type.SHORT);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						short windowId  = packetWrapper.get(Type.UNSIGNED_BYTE, 0);
						Windows windows = packetWrapper.user().get(Windows.class);
						short windowType = windows.get(windowId);

						short property = packetWrapper.get(Type.SHORT, 0);
						short value = packetWrapper.get(Type.SHORT, 1);

						if (windowType==-1) return;
						if (windowType==2) {  //Furnace
							Windows.Furnace furnace = windows.furnace.computeIfAbsent(windowId, x -> new Windows.Furnace());
							if (property==0 || property==1) {
								if (property==0) furnace.setFuelLeft(value);
								else furnace.setMaxFuel(value);
								if (furnace.getMaxFuel()==0) {
									packetWrapper.cancel();
									return;
								}
								value = (short) (200 * furnace.getFuelLeft() / furnace.getMaxFuel());
								packetWrapper.set(Type.SHORT, 0, (short) 1);
								packetWrapper.set(Type.SHORT, 1, value);
							} else if (property==2 || property==3) {
								if (property==2) furnace.setProgress(value);
								else furnace.setMaxProgress(value);
								if (furnace.getMaxProgress()==0) {
									packetWrapper.cancel();
									return;
								}
								value = (short) (200 * furnace.getProgress() / furnace.getMaxProgress());
								packetWrapper.set(Type.SHORT, 0, (short) 0);
								packetWrapper.set(Type.SHORT, 1, value);
							}
						} else if (windowType==4) {  //Enchanting Table
							if (property>2) {
								packetWrapper.cancel();
								return;
							}
						} else if (windowType==8) {
							windows.levelCost = value;
							windows.anvilId = windowId;
						}
					}
				});
			}
		});

		this.registerOutgoing(State.PLAY, 0x32, 0x32, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.BYTE);
				map(Type.SHORT);
				map(Type.BOOLEAN);
			}
		});

		//Update Sign
		this.registerOutgoing(State.PLAY, 0x33, 0x33, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Position position = packetWrapper.read(Type.POSITION);
						packetWrapper.write(Type.INT, position.getX().intValue());
						packetWrapper.write(Type.SHORT, position.getY().shortValue());
						packetWrapper.write(Type.INT, position.getZ().intValue());
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						for (int i = 0; i<4; i++) {
							String line = packetWrapper.read(Type.STRING);
							line = ChatUtil.jsonToLegacy(line);
							line = ChatUtil.removeUnusedColor(line, '0');
							if (line.length()>15) {
								line = ChatColor.stripColor(line);
								if (line.length()>15) line = line.substring(0, 15);
							}
							packetWrapper.write(Type.STRING, line);
						}
					}
				});
			}
		});

		//Map
		this.registerOutgoing(State.PLAY, 0x34, 0x34, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.cancel();
						int id = packetWrapper.read(Type.VAR_INT);
						byte scale = packetWrapper.read(Type.BYTE);

						int count = packetWrapper.read(Type.VAR_INT);
						byte[] icons = new byte[count*4];
						for (int i = 0; i < count; i++) {
							int j = packetWrapper.read(Type.BYTE);
							icons[i*4] = (byte)(j >> 4 & 0xF);
							icons[i*4+1] = packetWrapper.read(Type.BYTE);
							icons[i*4+2] = packetWrapper.read(Type.BYTE);
							icons[i*4+3] = (byte)(j & 0xF);
						}
						short columns = packetWrapper.read(Type.UNSIGNED_BYTE);
						if (columns > 0) {
							short rows = packetWrapper.read(Type.UNSIGNED_BYTE);
							byte x = packetWrapper.read(Type.BYTE);
							byte z = packetWrapper.read(Type.BYTE);
							Byte[] data = packetWrapper.read(Type.BYTE_ARRAY);

							for (int column = 0; column<columns; column++) {
								byte[] columnData = new byte[rows + 3];
								columnData[0] = 0;
								columnData[1] = (byte) (x + column);
								columnData[2] = z;

								for (int i = 0; i<rows; i++) {
									columnData[i+3] = data[column + i * columns];
								}

								PacketWrapper columnUpdate = new PacketWrapper(0x34, null, packetWrapper.user());
								columnUpdate.write(Type.VAR_INT, id);
								columnUpdate.write(Type.SHORT, (short)columnData.length);
								columnUpdate.write(new CustomByteType(columnData.length), columnData);

								PacketUtil.sendPacket(columnUpdate, Protocol1_7_6_10TO1_8.class, true, true);
							}
						}

						if (count>0) {
							byte[] iconData = new byte[count*3+1];
							iconData[0] = 1;
							for (int i = 0; i<count; i++) {
								iconData[i*3+1] = (byte)(icons[i*4] << 4 | icons[i*4+3] & 0xF);
								iconData[i*3+2] = icons[i*4+1];
								iconData[i*3+3] = icons[i*4+2];
							}
							PacketWrapper iconUpdate = new PacketWrapper(0x34, null, packetWrapper.user());
							iconUpdate.write(Type.VAR_INT, id);
							iconUpdate.write(Type.SHORT, (short)iconData.length);
							CustomByteType customByteType = new CustomByteType(iconData.length);
							iconUpdate.write(customByteType, iconData);
							PacketUtil.sendPacket(iconUpdate, Protocol1_7_6_10TO1_8.class, true, true);
						}

						PacketWrapper scaleUpdate = new PacketWrapper(0x34, null, packetWrapper.user());
						scaleUpdate.write(Type.VAR_INT, id);
						scaleUpdate.write(Type.SHORT, (short)2);
						scaleUpdate.write(new CustomByteType(2), new byte[] {2, scale});
						PacketUtil.sendPacket(scaleUpdate, Protocol1_7_6_10TO1_8.class, true, true);
					}
				});
			}
		});

		//Update Block Entity
		this.registerOutgoing(State.PLAY, 0x35, 0x35, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Position position = packetWrapper.read(Type.POSITION);
						packetWrapper.write(Type.INT, position.getX().intValue());
						packetWrapper.write(Type.SHORT, position.getY().shortValue());
						packetWrapper.write(Type.INT, position.getZ().intValue());
					}
				});
				map(Type.UNSIGNED_BYTE);  //Action
				map(Type.NBT, Types1_7_6_10.COMPRESSED_NBT);
			}
		});

		//Open Sign Editor
		this.registerOutgoing(State.PLAY, 0x36, 0x36, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Position position = packetWrapper.read(Type.POSITION);
						packetWrapper.write(Type.INT, position.getX().intValue());
						packetWrapper.write(Type.INT, position.getY().intValue());
						packetWrapper.write(Type.INT, position.getZ().intValue());
					}
				});
			}
		});

		//Player List Item
		this.registerOutgoing(State.PLAY, 0x38, 0x38, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.cancel();
						int action = packetWrapper.read(Type.VAR_INT);
						int count = packetWrapper.read(Type.VAR_INT);
						GameProfileStorage gameProfileStorage = packetWrapper.user().get(GameProfileStorage.class);
						for (int i = 0; i<count; i++) {
							UUID uuid = packetWrapper.read(Type.UUID);
							if (action==0) {
								String name = packetWrapper.read(Type.STRING);

								GameProfileStorage.GameProfile gameProfile = gameProfileStorage.get(uuid);
								if (gameProfile==null) gameProfile = gameProfileStorage.put(uuid, name);

								int propertyCount = packetWrapper.read(Type.VAR_INT);
								while (propertyCount-->0) {
									gameProfile.properties.add(new GameProfileStorage.Property(packetWrapper.read(Type.STRING), packetWrapper.read(Type.STRING),
											packetWrapper.read(Type.BOOLEAN) ? packetWrapper.read(Type.STRING) : null));
								}
								int gamemode = packetWrapper.read(Type.VAR_INT);
								int ping = packetWrapper.read(Type.VAR_INT);
								gameProfile.ping = ping;
								gameProfile.gamemode = gamemode;
								if (packetWrapper.read(Type.BOOLEAN)) {
									gameProfile.setDisplayName(packetWrapper.read(Type.STRING));
								}

								PacketWrapper packet = new PacketWrapper(0x38, null, packetWrapper.user());
								packet.write(Type.STRING, gameProfile.name);
								packet.write(Type.BOOLEAN, true);
								packet.write(Type.SHORT, (short) ping);
								PacketUtil.sendPacket(packet, Protocol1_7_6_10TO1_8.class);
							} else if (action==1) {
								int gamemode = packetWrapper.read(Type.VAR_INT);

								GameProfileStorage.GameProfile gameProfile = gameProfileStorage.get(uuid);
								if (gameProfile==null || gameProfile.gamemode==gamemode) continue;

								if (gamemode==3 || gameProfile.gamemode==3) {
									EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
									int entityId = tracker.getPlayerEntityId(uuid);
									if (entityId!=-1) {
										Item[] equipment;
										if (gamemode==3) {
											equipment = new Item[5];
											equipment[4] = gameProfile.getSkull();
										} else {
											equipment = tracker.getPlayerEquipment(uuid);
											if (equipment==null) equipment = new Item[5];
										}

										for (short slot = 0; slot<5; slot++) {
											PacketWrapper equipmentPacket = new PacketWrapper(0x04, null, packetWrapper.user());
											equipmentPacket.write(Type.INT, entityId);
											equipmentPacket.write(Type.SHORT, slot);
											equipmentPacket.write(Types1_7_6_10.COMPRESSED_NBT_ITEM, equipment[slot]);
											PacketUtil.sendPacket(equipmentPacket, Protocol1_7_6_10TO1_8.class);
										}
									}
								}

								gameProfile.gamemode = gamemode;
							} else if (action==2) {
								int ping = packetWrapper.read(Type.VAR_INT);

								GameProfileStorage.GameProfile gameProfile = gameProfileStorage.get(uuid);
								if (gameProfile==null) continue;

								gameProfile.ping = ping;

								PacketWrapper packet = new PacketWrapper(0x38, null, packetWrapper.user());
								packet.write(Type.STRING, gameProfile.name);
								packet.write(Type.BOOLEAN, true);
								packet.write(Type.SHORT, (short) ping);
								PacketUtil.sendPacket(packet, Protocol1_7_6_10TO1_8.class);
							} else if (action==3) {
								String displayName = packetWrapper.read(Type.BOOLEAN) ? packetWrapper.read(Type.STRING) : null;

								GameProfileStorage.GameProfile gameProfile = gameProfileStorage.get(uuid);
								if (gameProfile==null || gameProfile.displayName==null && displayName==null) continue;

								if (gameProfile.displayName==null && displayName!=null || gameProfile.displayName!=null && displayName==null || !gameProfile.displayName.equals(displayName)) {
									gameProfile.setDisplayName(displayName);
								}
							} else if (action==4) {
								GameProfileStorage.GameProfile gameProfile = gameProfileStorage.remove(uuid);
								if (gameProfile==null) continue;

								PacketWrapper packet = new PacketWrapper(0x38, null, packetWrapper.user());
								//packet.write(Type.STRING, gameProfile.getDisplayName());
								packet.write(Type.STRING, gameProfile.name);
								packet.write(Type.BOOLEAN, false);
								packet.write(Type.SHORT, (short) gameProfile.ping);
								PacketUtil.sendPacket(packet, Protocol1_7_6_10TO1_8.class);
							}
						}
					}
				});
			}
		});

		//Player Abilities
		this.registerOutgoing(State.PLAY, 0x39, 0x39, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.BYTE);
				map(Type.FLOAT);
				map(Type.FLOAT);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						byte flags = packetWrapper.get(Type.BYTE, 0);
						float flySpeed = packetWrapper.get(Type.FLOAT, 0);
						float walkSpeed = packetWrapper.get(Type.FLOAT, 1);
						PlayerAbilities abilities = packetWrapper.user().get(PlayerAbilities.class);
						abilities.setInvincible((flags & 8) == 8);
						abilities.setAllowFly((flags & 4) == 4);
						abilities.setFlying((flags & 2) == 2);
						abilities.setCreative((flags & 1) == 1);
						abilities.setFlySpeed(flySpeed);
						abilities.setWalkSpeed(walkSpeed);
						if (abilities.isSprinting() && abilities.isFlying()) {
							packetWrapper.set(Type.FLOAT, 0, abilities.getFlySpeed() * 2.0f);
						}
					}
				});
			}
		});

		//Scoreboard Objective
		this.registerOutgoing(State.PLAY, 0x3B, 0x3B, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						String name = packetWrapper.passthrough(Type.STRING);
						if (name.length()>16) {
							packetWrapper.set(Type.STRING, 0, name = name.substring(0, 16));
						}
						byte mode = packetWrapper.read(Type.BYTE);

						Scoreboard scoreboard = packetWrapper.user().get(Scoreboard.class);
						if (mode==0) {
							if (scoreboard.objectiveExists(name)) {
								packetWrapper.cancel();
								return;
							}
							scoreboard.addObjective(name);
						} else if (mode==1) {
							if (!scoreboard.objectiveExists(name)) {
								packetWrapper.cancel();
								return;
							}
							if (scoreboard.getColorIndependentSidebar() != null) {
								String username = packetWrapper.user().get(ProtocolInfo.class).getUsername();
								Optional<Byte> color = scoreboard.getPlayerTeamColor(username);
								if (color.isPresent()) {
									String sidebar = scoreboard.getColorDependentSidebar().get(color.get());
									if (name.equals(sidebar)) {
										PacketWrapper sidebarPacket = new PacketWrapper(0x3D, null, packetWrapper.user());
										sidebarPacket.write(Type.BYTE, (byte) 1);
										sidebarPacket.write(Type.STRING, scoreboard.getColorIndependentSidebar());
										PacketUtil.sendPacket(sidebarPacket, Protocol1_7_6_10TO1_8.class);
									}
								}
							}
							scoreboard.removeObjective(name);
						} else if (mode==2) {
							if (!scoreboard.objectiveExists(name)) {
								packetWrapper.cancel();
								return;
							}
						}

						if (mode==0 || mode==2) {
							String displayName = packetWrapper.passthrough(Type.STRING);
							if (displayName.length()>32) {
								packetWrapper.set(Type.STRING, 1, displayName.substring(0, 32));
							}
							packetWrapper.read(Type.STRING);
						} else {
							packetWrapper.write(Type.STRING, "");
						}
						packetWrapper.write(Type.BYTE, mode);
					}
				});
			}
		});

		//Update Score
		this.registerOutgoing(State.PLAY, 0x3C, 0x3C, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Scoreboard scoreboard = packetWrapper.user().get(Scoreboard.class);
						String name = packetWrapper.passthrough(Type.STRING);
						byte mode = packetWrapper.passthrough(Type.BYTE);

						if (mode==1) {
							name = scoreboard.removeTeamForScore(name);
						} else {
							name = scoreboard.sendTeamForScore(name);
						}

						if (name.length()>16) {
							name = ChatColor.stripColor(name);
							if (name.length()>16) {
								name = name.substring(0, 16);
							}
						}
						packetWrapper.set(Type.STRING, 0, name);

						String objective = packetWrapper.read(Type.STRING);
						if (objective.length()>16) {
							objective = objective.substring(0, 16);
						}

						if (mode!=1) {
							int score = packetWrapper.read(Type.VAR_INT);
							packetWrapper.write(Type.STRING, objective);
							packetWrapper.write(Type.INT, score);
						}
					}
				});
			}
		});

		// Display Scoreboard
		registerOutgoing(State.PLAY, 0x3D, 0x3D, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.BYTE); // Position
				map(Type.STRING); // Score name
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						byte position = packetWrapper.get(Type.BYTE, 0);
						String name = packetWrapper.get(Type.STRING, 0);
                        Scoreboard scoreboard = packetWrapper.user().get(Scoreboard.class);
                        if (position > 2) { // team specific sidebar
                            byte receiverTeamColor = (byte) (position - 3);
                            scoreboard.getColorDependentSidebar().put(receiverTeamColor, name);

                            String username = packetWrapper.user().get(ProtocolInfo.class).getUsername();
	                        Optional<Byte> color = scoreboard.getPlayerTeamColor(username);
	                        if (color.isPresent() && color.get() == receiverTeamColor) {
		                        position = 1;
	                        } else {
		                        position = -1;
	                        }
                        } else if (position == 1) { // team independent sidebar
						    scoreboard.setColorIndependentSidebar(name);
						    String username = packetWrapper.user().get(ProtocolInfo.class).getUsername();
	                        Optional<Byte> color = scoreboard.getPlayerTeamColor(username);
	                        if (color.isPresent() && scoreboard.getColorDependentSidebar().containsKey(color.get())) {
		                        position = -1;
	                        }
                        }
						if (position == -1) {
                            packetWrapper.cancel();
                            return;
                        }
                        packetWrapper.set(Type.BYTE, 0, position);
					}
				});
			}
		});

		//Scoreboard Teams
		this.registerOutgoing(State.PLAY, 0x3E, 0x3E, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.STRING);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						String team = packetWrapper.get(Type.STRING, 0);
						if (team==null) {
							packetWrapper.cancel();
							return;
						}
						byte mode = packetWrapper.passthrough(Type.BYTE);

						Scoreboard scoreboard = packetWrapper.user().get(Scoreboard.class);

						if (mode!=0 && !scoreboard.teamExists(team)) {
							packetWrapper.cancel();
							return;
						} else if (mode==0 && scoreboard.teamExists(team)) {
							scoreboard.removeTeam(team);

							PacketWrapper remove = new PacketWrapper(0x3E, null, packetWrapper.user());
							remove.write(Type.STRING, team);
							remove.write(Type.BYTE, (byte)1);
							PacketUtil.sendPacket(remove, Protocol1_7_6_10TO1_8.class, true, true);
						}

						if (mode==0) scoreboard.addTeam(team);
						else if (mode==1) scoreboard.removeTeam(team);

						if (mode==0 || mode==2) {
							packetWrapper.passthrough(Type.STRING); // Display name
							packetWrapper.passthrough(Type.STRING); // prefix
							packetWrapper.passthrough(Type.STRING); // suffix
							packetWrapper.passthrough(Type.BYTE); // friendly fire
							packetWrapper.read(Type.STRING); // name tag visibility
							byte color = packetWrapper.read(Type.BYTE);
							if (mode == 2 && scoreboard.getTeamColor(team).get() != color) {
								String username = packetWrapper.user().get(ProtocolInfo.class).getUsername();
								String sidebar = scoreboard.getColorDependentSidebar().get(color);
								PacketWrapper sidebarPacket = packetWrapper.create(0x3D);
								sidebarPacket.write(Type.BYTE, (byte) 1);
								sidebarPacket.write(Type.STRING, sidebar == null ? "" : sidebar);
								PacketUtil.sendPacket(sidebarPacket, Protocol1_7_6_10TO1_8.class);
							}
							scoreboard.setTeamColor(team, color);
						}
						if (mode==0 || mode==3 || mode==4) {
							byte color = scoreboard.getTeamColor(team).get();
							int size = packetWrapper.read(Type.VAR_INT);
							List<String> entryList = new ArrayList<>();

							for (int i = 0; i<size; i++) {
								String entry = packetWrapper.read(Type.STRING);
								if (entry==null) continue;
								String username = packetWrapper.user().get(ProtocolInfo.class).getUsername();

								if (mode == 4) {
									if (!scoreboard.isPlayerInTeam(entry, team)) continue;
									scoreboard.removePlayerFromTeam(entry, team);
									if (entry.equals(username)) {
										PacketWrapper sidebarPacket = packetWrapper.create(0x3D);
										sidebarPacket.write(Type.BYTE, (byte) 1);
										sidebarPacket.write(Type.STRING, scoreboard.getColorIndependentSidebar() == null ? "" : scoreboard.getColorIndependentSidebar());
										PacketUtil.sendPacket(sidebarPacket, Protocol1_7_6_10TO1_8.class);
									}
								} else {
									scoreboard.addPlayerToTeam(entry, team);
									if (entry.equals(username) && scoreboard.getColorDependentSidebar().containsKey(color)) {
										PacketWrapper displayObjective = packetWrapper.create(0x3D);
										displayObjective.write(Type.BYTE, (byte) 1);
										displayObjective.write(Type.STRING, scoreboard.getColorDependentSidebar().get(color));
										PacketUtil.sendPacket(displayObjective, Protocol1_7_6_10TO1_8.class);
									}
								}
								entryList.add(entry);
							}

							packetWrapper.write(Type.SHORT, (short)entryList.size());
							for (String entry : entryList) {
								packetWrapper.write(Type.STRING, entry);
							}
						}
					}
				});
			}
		});

		//Custom Payload
		this.registerOutgoing(State.PLAY, 0x3F, 0x3F, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.STRING);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						String channel = packetWrapper.get(Type.STRING, 0);
						if (channel.equalsIgnoreCase("MC|TrList")) {
							packetWrapper.write(Type.SHORT, (short)0);  //Size Placeholder

							ByteBuf buf = Unpooled.buffer();

							Type.INT.write(buf, packetWrapper.passthrough(Type.INT));  //Window Id

							int size = packetWrapper.passthrough(Type.BYTE);  //Size
							Type.BYTE.write(buf, (byte)size);

							for (int i = 0; i < size; i++) {
								Item item = ItemRewriter.toClient(packetWrapper.read(Type.ITEM));
								packetWrapper.write(Types1_7_6_10.COMPRESSED_NBT_ITEM, item); //Buy Item 1
								Types1_7_6_10.COMPRESSED_NBT_ITEM.write(buf, item);

								item = ItemRewriter.toClient(packetWrapper.read(Type.ITEM));
								packetWrapper.write(Types1_7_6_10.COMPRESSED_NBT_ITEM, item); //Buy Item 3
								Types1_7_6_10.COMPRESSED_NBT_ITEM.write(buf, item);

								boolean has3Items = packetWrapper.passthrough(Type.BOOLEAN);
								Type.BOOLEAN.write(buf, has3Items);
								if (has3Items) {
									item = ItemRewriter.toClient(packetWrapper.read(Type.ITEM));
									packetWrapper.write(Types1_7_6_10.COMPRESSED_NBT_ITEM, item); //Buy Item 2
									Types1_7_6_10.COMPRESSED_NBT_ITEM.write(buf, item);
								}

								Type.BOOLEAN.write(buf, packetWrapper.passthrough(Type.BOOLEAN)); //Unavailable
								packetWrapper.read(Type.INT); //Uses
								packetWrapper.read(Type.INT); //Max Uses
							}

							packetWrapper.set(Type.SHORT, 0, (short)buf.readableBytes());
							buf.release();
						} else {
							byte[] data = packetWrapper.read(Type.REMAINING_BYTES);
							packetWrapper.write(Type.SHORT, (short)data.length);
							packetWrapper.write(Type.REMAINING_BYTES, data);
						}
					}
				});
			}
		});

		//Server Difficulty
		this.registerOutgoing(State.PLAY, 0x41, -1, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.cancel();
					}
				});
			}
		});

		//Combat Event
		this.registerOutgoing(State.PLAY, 0x42, -1, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.cancel();
					}
				});
			}
		});

		//Camera
		this.registerOutgoing(State.PLAY, 0x43, -1, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.cancel();

						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);

						int entityId = packetWrapper.read(Type.VAR_INT);
						int spectating = tracker.getSpectating();

						if (spectating!=entityId) {
							tracker.setSpectating(entityId);
						}
					}
				});
			}
		});

		//World Border
		this.registerOutgoing(State.PLAY, 0x44, -1, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int action = packetWrapper.read(Type.VAR_INT);
						WorldBorder worldBorder = packetWrapper.user().get(WorldBorder.class);
						if (action==0) {
							worldBorder.setSize(packetWrapper.read(Type.DOUBLE));
						} else if (action==1) {
							worldBorder.lerpSize(
									packetWrapper.read(Type.DOUBLE),
									packetWrapper.read(Type.DOUBLE),
									packetWrapper.read(VarLongType.VAR_LONG)
							);
						} else if (action==2) {
							worldBorder.setCenter(
									packetWrapper.read(Type.DOUBLE),
									packetWrapper.read(Type.DOUBLE)
							);
						} else if (action==3) {
							worldBorder.init(
									packetWrapper.read(Type.DOUBLE),
									packetWrapper.read(Type.DOUBLE),
									packetWrapper.read(Type.DOUBLE),
									packetWrapper.read(Type.DOUBLE),
									packetWrapper.read(VarLongType.VAR_LONG),
									packetWrapper.read(Type.VAR_INT),
									packetWrapper.read(Type.VAR_INT),
									packetWrapper.read(Type.VAR_INT)
							);
						} else if (action==4) {
							worldBorder.setWarningTime(packetWrapper.read(Type.VAR_INT));
						} else if (action==5) {
							worldBorder.setWarningBlocks(packetWrapper.read(Type.VAR_INT));
						}

						packetWrapper.cancel();
					}
				});
			}
		});

		//Title
		this.registerOutgoing(State.PLAY, 0x45, -1, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.cancel();
						TitleRenderProvider titleRenderProvider = Via.getManager().getProviders().get(TitleRenderProvider.class);
						if (titleRenderProvider==null) return;
						int action = packetWrapper.read(Type.VAR_INT);
						UUID uuid = Utils.getUUID(packetWrapper.user());
						switch (action) {
							case 0:
								titleRenderProvider.setTitle(uuid, packetWrapper.read(Type.STRING));
								break;
							case 1:
								titleRenderProvider.setSubTitle(uuid, packetWrapper.read(Type.STRING));
								break;
							case 2:
								titleRenderProvider.setTimings(uuid, packetWrapper.read(Type.INT), packetWrapper.read(Type.INT), packetWrapper.read(Type.INT));
								break;
							case 3:
								titleRenderProvider.clear(uuid);
								break;
							case 4:
								titleRenderProvider.reset(uuid);
								break;
						}
					}
				});
			}
		});

		//Set Compression
		this.registerOutgoing(State.PLAY, 0x46, -1, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.cancel();
					}
				});
			}
		});

		//Player List Header And Footer
		this.registerOutgoing(State.PLAY, 0x47, -1, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.cancel();
					}
				});
			}
		});

		//Resource Pack Send
		this.registerOutgoing(State.PLAY, 0x48, -1, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.cancel();
					}
				});
			}
		});

		//Update Entity NBT
		this.registerOutgoing(State.PLAY, 0x49, -1, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.cancel();
					}
				});
			}
		});

		//Keep Alive
		this.registerIncoming(State.PLAY, 0x00, 0x00, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.INT, Type.VAR_INT);
			}
		});

		//Chat Message
		this.registerIncoming(State.PLAY, 0x01, 0x01, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.STRING);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						String msg = packetWrapper.get(Type.STRING, 0);
						int gamemode = packetWrapper.user().get(EntityTracker.class).getGamemode();
						if (gamemode==3 && msg.toLowerCase().startsWith("/stp ")) {
							String username = msg.split(" ")[1];
							GameProfileStorage storage = packetWrapper.user().get(GameProfileStorage.class);
							GameProfileStorage.GameProfile profile = storage.get(username, true);
							if (profile!=null && profile.uuid!=null) {
								packetWrapper.cancel();

								PacketWrapper teleportPacket = new PacketWrapper(0x18, null, packetWrapper.user());
								teleportPacket.write(Type.UUID, profile.uuid);

								try {
									PacketUtil.sendToServer(teleportPacket, Protocol1_7_6_10TO1_8.class, true, true);
								} catch (CancelException ignored) {
									;
								} catch (Exception ex) {
									ex.printStackTrace();
								}
							}
						}
					}
				});
			}
		});

		//Use Entity
		this.registerIncoming(State.PLAY, 0x02, 0x02, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.INT, Type.VAR_INT);
				map(Type.BYTE, Type.VAR_INT);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int mode = packetWrapper.get(Type.VAR_INT, 1);
						if (mode!=0) return;
						int entityId = packetWrapper.get(Type.VAR_INT, 0);
						EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
						EntityReplacement replacement = tracker.getEntityReplacement(entityId);
						if (!(replacement instanceof ArmorStandReplacement)) return;
						ArmorStandReplacement armorStand = (ArmorStandReplacement) replacement;
						AABB boundingBox = armorStand.getBoundingBox();
						PlayerPosition playerPosition = packetWrapper.user().get(PlayerPosition.class);
						Vector3d pos = new Vector3d(playerPosition.getPosX(), playerPosition.getPosY() + 1.8, playerPosition.getPosZ());
						double yaw = Math.toRadians(playerPosition.getYaw());
						double pitch = Math.toRadians(playerPosition.getPitch());
						Vector3d dir = new Vector3d(-Math.cos(pitch) * Math.sin(yaw), -Math.sin(pitch), Math.cos(pitch) * Math.cos(yaw));
						Ray3d ray = new Ray3d(pos, dir);
						Vector3d intersection = RayTracing.trace(ray, boundingBox, 5.0);
						if (intersection==null) return;
						intersection.substract(boundingBox.getMin());
						mode = 2;
						packetWrapper.set(Type.VAR_INT, 1, mode);
						packetWrapper.write(Type.FLOAT, (float) intersection.getX());
						packetWrapper.write(Type.FLOAT, (float) intersection.getY());
						packetWrapper.write(Type.FLOAT, (float) intersection.getZ());
					}
				});
			}
		});

		//Player
		this.registerIncoming(State.PLAY, 0x03, 0x03, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.BOOLEAN);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						PlayerPosition playerPosition = packetWrapper.user().get(PlayerPosition.class);
						playerPosition.setOnGround(packetWrapper.get(Type.BOOLEAN, 0));
					}
				});
			}
		});

		//Player Position
		this.registerIncoming(State.PLAY, 0x04, 0x04, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.DOUBLE);  //X
				map(Type.DOUBLE);  //Y
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.read(Type.DOUBLE);
					}
				});
				map(Type.DOUBLE);  //Z
				map(Type.BOOLEAN);  //OnGround
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						double x = packetWrapper.get(Type.DOUBLE, 0);
						double feetY = packetWrapper.get(Type.DOUBLE, 1);
						double z = packetWrapper.get(Type.DOUBLE, 2);

						PlayerPosition playerPosition = packetWrapper.user().get(PlayerPosition.class);

						if (playerPosition.isPositionPacketReceived()) {
							playerPosition.setPositionPacketReceived(false);
							feetY -= 0.01;
							packetWrapper.set(Type.DOUBLE, 1, feetY);
						}

						playerPosition.setOnGround(packetWrapper.get(Type.BOOLEAN, 0));
						playerPosition.setPos(x, feetY, z);
					}
				});
			}
		});

		//Player Look
		this.registerIncoming(State.PLAY, 0x05, 0x05, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.FLOAT);
				map(Type.FLOAT);
				map(Type.BOOLEAN);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						PlayerPosition playerPosition = packetWrapper.user().get(PlayerPosition.class);
						playerPosition.setYaw(packetWrapper.get(Type.FLOAT, 0));
						playerPosition.setPitch(packetWrapper.get(Type.FLOAT, 1));
						playerPosition.setOnGround(packetWrapper.get(Type.BOOLEAN, 0));
					}
				});
			}
		});

		//Player Position And Look
		this.registerIncoming(State.PLAY, 0x06, 0x06, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.DOUBLE);  //X
				map(Type.DOUBLE);  //Y
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.read(Type.DOUBLE);
					}
				});
				map(Type.DOUBLE);  //Z
				map(Type.FLOAT);  //Yaw
				map(Type.FLOAT);  //Pitch
				map(Type.BOOLEAN);  //OnGround
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						double x = packetWrapper.get(Type.DOUBLE, 0);
						double feetY = packetWrapper.get(Type.DOUBLE, 1);
						double z = packetWrapper.get(Type.DOUBLE, 2);

						float yaw = packetWrapper.get(Type.FLOAT, 0);
						float pitch = packetWrapper.get(Type.FLOAT, 1);

						PlayerPosition playerPosition = packetWrapper.user().get(PlayerPosition.class);

						if (playerPosition.isPositionPacketReceived()) {
							playerPosition.setPositionPacketReceived(false);
							feetY = playerPosition.getReceivedPosY();
							packetWrapper.set(Type.DOUBLE, 1, feetY);
						}

						playerPosition.setOnGround(packetWrapper.get(Type.BOOLEAN, 0));
						playerPosition.setPos(x, feetY, z);
						playerPosition.setYaw(yaw);
						playerPosition.setPitch(pitch);
					}
				});
			}
		});

		//Player Digging
		this.registerIncoming(State.PLAY, 0x07, 0x07, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.BYTE);  //Status
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						long x = packetWrapper.read(Type.INT);
						long y = packetWrapper.read(Type.UNSIGNED_BYTE);
						long z = packetWrapper.read(Type.INT);
						packetWrapper.write(Type.POSITION, new Position(x, y, z));
					}
				});
				map(Type.BYTE);  //Face
			}
		});

		//Player Block Placement
		this.registerIncoming(State.PLAY, 0x08, 0x08, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						long x = packetWrapper.read(Type.INT);
						long y = packetWrapper.read(Type.UNSIGNED_BYTE);
						long z = packetWrapper.read(Type.INT);
						packetWrapper.write(Type.POSITION, new Position(x, y, z));

						packetWrapper.passthrough(Type.BYTE);  //Direction
						Item item = packetWrapper.read(Types1_7_6_10.COMPRESSED_NBT_ITEM);
						item = ItemRewriter.toServer(item);
						packetWrapper.write(Type.ITEM, item);

						for (int i = 0; i<3; i++) {
							packetWrapper.passthrough(Type.BYTE);
						}
					}
				});
			}
		});

		//Animation
		this.registerIncoming(State.PLAY, 0x0A, 0x0A, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int entityId = packetWrapper.read(Type.INT);
						int animation = packetWrapper.read(Type.BYTE);  //Animation
						if (animation==1) return;
						packetWrapper.cancel();
						//1.7 vanilla client is not sending this packet with animation!=1
						switch (animation) {
							case 104:
								animation = 0;
								break;
							case 105:
								animation = 1;
								break;
							case 3:
								animation = 2;
								break;
							default:
								return;
						}
						PacketWrapper entityAction = new PacketWrapper(0x0B, null, packetWrapper.user());
						entityAction.write(Type.VAR_INT, entityId);
						entityAction.write(Type.VAR_INT, animation);
						entityAction.write(Type.VAR_INT, 0);
						PacketUtil.sendPacket(entityAction, Protocol1_7_6_10TO1_8.class, true, true);
					}
				});
			}
		});

		//Entity Action
		this.registerIncoming(State.PLAY, 0x0B, 0x0B, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.INT, Type.VAR_INT);  //Entity Id
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.write(Type.VAR_INT, packetWrapper.read(Type.BYTE)-1);
					}
				});  //Action Id
				map(Type.INT, Type.VAR_INT);  //Action Paramter
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int action = packetWrapper.get(Type.VAR_INT, 1);
						if (action==3 || action==4) {
							PlayerAbilities abilities = packetWrapper.user().get(PlayerAbilities.class);
							abilities.setSprinting(action==3);
							PacketWrapper abilitiesPacket = new PacketWrapper(0x39, null, packetWrapper.user());
							abilitiesPacket.write(Type.BYTE, abilities.getFlags());
							abilitiesPacket.write(Type.FLOAT, abilities.isSprinting() ? abilities.getFlySpeed() * 2.0f : abilities.getFlySpeed());
							abilitiesPacket.write(Type.FLOAT, abilities.getWalkSpeed());
							PacketUtil.sendPacket(abilitiesPacket, Protocol1_7_6_10TO1_8.class);
						}
					}
				});
			}
		});

		//Steer Vehicle
		this.registerIncoming(State.PLAY, 0x0C, 0x0C, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.FLOAT);  //Sideways
				map(Type.FLOAT);  //Forwards
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						boolean jump = packetWrapper.read(Type.BOOLEAN);
						boolean unmount = packetWrapper.read(Type.BOOLEAN);
						short flags = 0;
						if (jump) flags += 0x01;
						if (unmount) flags += 0x02;
						packetWrapper.write(Type.UNSIGNED_BYTE, flags);

						if (unmount) {
							EntityTracker tracker = packetWrapper.user().get(EntityTracker.class);
							if (tracker.getSpectating()!=tracker.getPlayerId()) {
								PacketWrapper sneakPacket = new PacketWrapper(0x0B, null, packetWrapper.user());
								sneakPacket.write(Type.VAR_INT, tracker.getPlayerId());
								sneakPacket.write(Type.VAR_INT, 0);  //Start sneaking
								sneakPacket.write(Type.VAR_INT, 0);  //Action Parameter

								PacketWrapper unsneakPacket = new PacketWrapper(0x0B, null, packetWrapper.user());
								unsneakPacket.write(Type.VAR_INT, tracker.getPlayerId());
								unsneakPacket.write(Type.VAR_INT, 1);  //Stop sneaking
								unsneakPacket.write(Type.VAR_INT, 0);  //Action Parameter

								try {
									PacketUtil.sendToServer(sneakPacket, Protocol1_7_6_10TO1_8.class, true, false);
								} catch (CancelException ignored) {
									;
								} catch (Exception ex) {
									ex.printStackTrace();
								}
							}
						}
					}
				});
			}
		});

		//Close Window
		this.registerIncoming(State.PLAY, 0x0D, 0x0D, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.UNSIGNED_BYTE);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						short windowsId = packetWrapper.get(Type.UNSIGNED_BYTE, 0);
						packetWrapper.user().get(Windows.class).remove(windowsId);
					}
				});
			}
		});

		//Click Window
		this.registerIncoming(State.PLAY, 0x0E, 0x0E, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						short windowId = packetWrapper.read(Type.BYTE);  //Window Id
						packetWrapper.write(Type.UNSIGNED_BYTE, windowId);
						short windowType = packetWrapper.user().get(Windows.class).get(windowId);
						short slot = packetWrapper.read(Type.SHORT);
						if (windowType==4) {
							if (slot>0) {
								slot += 1;
							}
						}
						packetWrapper.write(Type.SHORT, slot);
					}
				});
				map(Type.BYTE);  //Button
				map(Type.SHORT);  //Action Number
				map(Type.BYTE);  //Mode
				map(Types1_7_6_10.COMPRESSED_NBT_ITEM, Type.ITEM);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Item item = packetWrapper.get(Type.ITEM, 0);
						ItemRewriter.toServer(item);
						packetWrapper.set(Type.ITEM, 0, item);
					}
				});
			}
		});

		//Confirm Transaction
		this.registerIncoming(State.PLAY, 0x0F, 0x0F, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.BYTE);
				map(Type.SHORT);
				map(Type.BOOLEAN);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int action = packetWrapper.get(Type.SHORT, 0);
						if (action==-89) packetWrapper.cancel();
					}
				});
			}
		});

		//Creative Inventory Action
		this.registerIncoming(State.PLAY, 0x10, 0x10, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.SHORT);  //Slot
				map(Types1_7_6_10.COMPRESSED_NBT_ITEM, Type.ITEM);  //Item
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						Item item = packetWrapper.get(Type.ITEM, 0);
						ItemRewriter.toServer(item);
						packetWrapper.set(Type.ITEM, 0, item);
					}
				});
			}
		});

		//Update Sign
		this.registerIncoming(State.PLAY, 0x12, 0x12, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						long x = packetWrapper.read(Type.INT);
						long y = packetWrapper.read(Type.SHORT);
						long z = packetWrapper.read(Type.INT);
						packetWrapper.write(Type.POSITION, new Position(x, y, z));
						for (int i = 0; i<4; i++) {
							String line = packetWrapper.read(Type.STRING);
							line = ChatUtil.legacyToJson(line);
							packetWrapper.write(Type.STRING, line);
						}
					}
				});
			}
		});

		//Player Abilities
		this.registerIncoming(State.PLAY, 0x13, 0x13, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.BYTE);
				map(Type.FLOAT);
				map(Type.FLOAT);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						byte flags = packetWrapper.get(Type.BYTE, 0);
						PlayerAbilities abilities = packetWrapper.user().get(PlayerAbilities.class);
						abilities.setAllowFly((flags & 4) == 4);
						abilities.setFlying((flags & 2) == 2);
						packetWrapper.set(Type.FLOAT, 0, abilities.getFlySpeed());
					}
				});
			}
		});

		//Tab-Complete
		this.registerIncoming(State.PLAY, 0x14, 0x14, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.STRING);
				create(new ValueCreator() {
					@Override
					public void write(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.write(Type.BOOLEAN, false);
					}
				});
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						String msg = packetWrapper.get(Type.STRING, 0);
						if (msg.toLowerCase().startsWith("/stp ")) {
							packetWrapper.cancel();
							String[] args = msg.split(" ");
							if (args.length<=2) {
								String prefix = args.length==1 ? "" : args[1];
								GameProfileStorage storage = packetWrapper.user().get(GameProfileStorage.class);
								List<GameProfileStorage.GameProfile> profiles = storage.getAllWithPrefix(prefix, true);

								PacketWrapper tabComplete = new PacketWrapper(0x3A, null, packetWrapper.user());
								tabComplete.write(Type.VAR_INT, profiles.size());
								for (GameProfileStorage.GameProfile profile : profiles) {
									tabComplete.write(Type.STRING, profile.name);
								}

								PacketUtil.sendPacket(tabComplete, Protocol1_7_6_10TO1_8.class);
							}
						}
					}
				});
			}
		});

		//Client Settings
		this.registerIncoming(State.PLAY, 0x15, 0x15, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.STRING);
				map(Type.BYTE);
				map(Type.BYTE);
				map(Type.BOOLEAN);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.read(Type.BYTE);

						boolean cape = packetWrapper.read(Type.BOOLEAN);
						packetWrapper.write(Type.UNSIGNED_BYTE, (short)(cape ? 127 : 126));
					}
				});
			}
		});

		//Custom Payload
		this.registerIncoming(State.PLAY, 0x17, 0x17, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.STRING);
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						String channel = packetWrapper.get(Type.STRING, 0);
						if (channel.equalsIgnoreCase("MC|ItemName")) {
							int length = packetWrapper.read(Type.SHORT);
							CustomByteType customByteType = new CustomByteType(length);
							byte[] data = packetWrapper.read(customByteType);
							String name = new String(data, Charsets.UTF_8);
							ByteBuf buf = Unpooled.buffer();
							Type.STRING.write(buf, name);
							data = new byte[buf.readableBytes()];
							buf.readBytes(data);
							buf.release();
							packetWrapper.write(Type.REMAINING_BYTES, data);

							Windows windows = packetWrapper.user().get(Windows.class);
							PacketWrapper updateCost = new PacketWrapper(0x31, null, packetWrapper.user());
							updateCost.write(Type.UNSIGNED_BYTE, windows.anvilId);
							updateCost.write(Type.SHORT, (short) 0);
							updateCost.write(Type.SHORT, windows.levelCost);

							PacketUtil.sendPacket(updateCost, Protocol1_7_6_10TO1_8.class, true, true);
						} else if (channel.equalsIgnoreCase("MC|BEdit") || channel.equalsIgnoreCase("MC|BSign")) {
							packetWrapper.read(Type.SHORT); //length
							Item book = packetWrapper.read(Types1_7_6_10.COMPRESSED_NBT_ITEM);
							CompoundTag tag = book.getTag();
							if (tag!=null && tag.contains("pages")) {
								ListTag pages = tag.get("pages");
								for (int i = 0; i<pages.size(); i++) {
									StringTag page = pages.get(i);
									String value = page.getValue();
									value = ChatUtil.legacyToJson(value);
									page.setValue(value);
								}
							}
							packetWrapper.write(Type.ITEM, book);
						} else {
							int length = packetWrapper.read(Type.SHORT);
							CustomByteType customByteType = new CustomByteType(length);
							byte[] data = packetWrapper.read(customByteType);
							packetWrapper.write(Type.REMAINING_BYTES, data);
						}
					}
				});
			}
		});

		//Disconnect
		this.registerOutgoing(State.LOGIN, 0x00, 0x00, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.STRING);
			}
		});

		//Encryption Request
		this.registerOutgoing(State.LOGIN, 0x01, 0x01, new PacketRemapper() {
			@Override
			public void registerMap() {
				map(Type.STRING);  //Server ID
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int publicKeyLength = packetWrapper.read(Type.VAR_INT);
						packetWrapper.write(Type.SHORT, (short)publicKeyLength);
						for (int i = 0; i<publicKeyLength; i++) {
							packetWrapper.passthrough(Type.BYTE);
						}
						int verifyTokenLength = packetWrapper.read(Type.VAR_INT);
						packetWrapper.write(Type.SHORT, (short)verifyTokenLength);
						for (int i = 0; i<verifyTokenLength; i++) {
							packetWrapper.passthrough(Type.BYTE);
						}
					}
				});
			}
		});

		//Set Compression
		this.registerOutgoing(State.LOGIN, 0x03, 0x03, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						packetWrapper.cancel();
						packetWrapper.user().get(CompressionSendStorage.class).setCompressionSend(true);
					}
				});
			}
		});

		//Encryption Response
		this.registerIncoming(State.LOGIN, 0x01, 0x01, new PacketRemapper() {
			@Override
			public void registerMap() {
				handler(new PacketHandler() {
					@Override
					public void handle(PacketWrapper packetWrapper) throws Exception {
						int sharedSecretLength = packetWrapper.read(Type.SHORT);
						packetWrapper.write(Type.VAR_INT, sharedSecretLength);
						for (int i = 0; i<sharedSecretLength; i++) {
							packetWrapper.passthrough(Type.BYTE);
						}
						int verifyTokenLength = packetWrapper.read(Type.SHORT);
						packetWrapper.write(Type.VAR_INT, verifyTokenLength);
						for (int i = 0; i<verifyTokenLength; i++) {
							packetWrapper.passthrough(Type.BYTE);
						}
					}
				});
			}
		});
	}

	@Override
	public void transform(Direction direction, State state, PacketWrapper packetWrapper) throws Exception {
		CompressionSendStorage compressionSendStorage = packetWrapper.user().get(CompressionSendStorage.class);
		if (compressionSendStorage.isCompressionSend()) {
			Channel channel = packetWrapper.user().getChannel();
			channel.pipeline().replace("decompress", "decompress", new EmptyChannelHandler());
			channel.pipeline().replace("compress", "compress", new ForwardMessageToByteEncoder());

			compressionSendStorage.setCompressionSend(false);
		}

		super.transform(direction, state, packetWrapper);
	}

	@Override
	public void init(UserConnection userConnection) {
		Ticker.init();

		userConnection.put(new Windows(userConnection));
		userConnection.put(new EntityTracker(userConnection));
		userConnection.put(new PlayerPosition(userConnection));
		userConnection.put(new GameProfileStorage(userConnection));
		userConnection.put(new ClientChunks(userConnection));
		userConnection.put(new Scoreboard(userConnection));
		userConnection.put(new CompressionSendStorage(userConnection));
		userConnection.put(new WorldBorder(userConnection));
		userConnection.put(new PlayerAbilities(userConnection));
		userConnection.put(new ClientWorld(userConnection));
	}
}
