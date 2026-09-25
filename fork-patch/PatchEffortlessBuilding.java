import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Patches the 32-bit item-count overflow in Effortless Building 4.2 for NeoForge. */
public final class PatchEffortlessBuilding {
    private static final String CLASS_ENTRY =
            "neoforge/nl/requios/effortlessbuilding/utilities/ItemUsageTracker.class";
    private static final String CLASS_NAME =
            "neoforge/nl/requios/effortlessbuilding/utilities/ItemUsageTracker";
    private static final String COMPUTE_DESCRIPTOR =
            "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/Item;Ljava/util/List;Z)V";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: PatchEffortlessBuilding input.jar output.jar");
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        if (Files.exists(output)) throw new IOException("Output already exists: " + output);
        Files.createDirectories(output.getParent());
        boolean found = false;
        try (JarFile source = new JarFile(input.toFile());
             JarOutputStream target = new JarOutputStream(Files.newOutputStream(output))) {
            Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                byte[] data = source.getInputStream(entry).readAllBytes();
                if (CLASS_ENTRY.equals(entry.getName())) {
                    data = patchClass(data);
                    found = true;
                }
                JarEntry copy = new JarEntry(entry.getName());
                copy.setTime(entry.getTime());
                target.putNextEntry(copy);
                target.write(data);
                target.closeEntry();
            }
        }
        if (!found) {
            Files.deleteIfExists(output);
            throw new IllegalStateException("Target class not found");
        }
        System.out.println("Patched: " + output);
    }

    private static byte[] patchClass(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        int[] replaced = {0};
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"computeItem".equals(name) || !COMPUTE_DESCRIPTOR.equals(descriptor)) return delegate;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.IADD && replaced[0]++ == 0) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, CLASS_NAME,
                                    "saturatingAdd", "(II)I", false);
                        } else {
                            super.visitInsn(opcode);
                        }
                    }
                };
            }

            @Override
            public void visitEnd() {
                MethodVisitor method = super.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        "saturatingAdd", "(II)I", null, null);
                method.visitCode();
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitInsn(Opcodes.I2L);
                method.visitVarInsn(Opcodes.ILOAD, 1);
                method.visitInsn(Opcodes.I2L);
                method.visitInsn(Opcodes.LADD);
                method.visitLdcInsn((long) Integer.MAX_VALUE);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(JJ)J", false);
                method.visitInsn(Opcodes.L2I);
                method.visitInsn(Opcodes.IRETURN);
                method.visitMaxs(4, 2);
                method.visitEnd();
                super.visitEnd();
            }
        };
        reader.accept(visitor, 0);
        if (replaced[0] != 1) throw new IllegalStateException("Expected one addition in computeItem, found " + replaced[0]);
        return writer.toByteArray();
    }
}
