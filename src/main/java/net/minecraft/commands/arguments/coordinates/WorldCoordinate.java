package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.math.BigDecimal;
import net.minecraft.network.chat.Component;

public class WorldCoordinate {
   private static final char PREFIX_RELATIVE = '~';
   public static final SimpleCommandExceptionType ERROR_EXPECTED_DOUBLE = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.double"));
   public static final SimpleCommandExceptionType ERROR_EXPECTED_INT = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.int"));
   private final boolean relative;
   private final double value;
   private final String literal;

   public WorldCoordinate(boolean relative, double value) {
      this(relative, value, Double.toString(value));
   }

   private WorldCoordinate(boolean relative, double value, String literal) {
      this.relative = relative;
      this.value = value;
      this.literal = literal;
   }

   public double get(double p_120868_) {
      return this.relative ? this.value + p_120868_ : this.value;
   }

   public double getValue() { return this.value; }
   public String getLiteral() { return this.literal; }

   public static WorldCoordinate parseDouble(StringReader p_120872_, boolean p_120873_) throws CommandSyntaxException {
      if (p_120872_.canRead() && p_120872_.peek() == '^') {
         throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(p_120872_);
      } else if (!p_120872_.canRead()) {
         throw ERROR_EXPECTED_DOUBLE.createWithContext(p_120872_);
      } else {
         boolean flag = isRelative(p_120872_);
         int i = p_120872_.getCursor();
         String s = p_120872_.canRead() && p_120872_.peek() != ' ' ? readCoordinateToken(p_120872_) : "";
         if (flag && s.isEmpty()) {
            return new WorldCoordinate(true, 0.0D, "0");
         } else {
            try {
               String literal = s.isEmpty() ? "0" : s;
               if (!isDecimal(literal)) throw new NumberFormatException(literal);
               double d0 = Double.parseDouble(literal);
               if (!s.contains(".") && !flag && p_120873_) {
                  d0 += 0.5D;
                  literal = new BigDecimal(literal).add(BigDecimal.valueOf(0.5D)).toPlainString();
               }
               return new WorldCoordinate(flag, d0, literal);
            } catch (NumberFormatException exception) {
               p_120872_.setCursor(i);
               throw ERROR_EXPECTED_DOUBLE.createWithContext(p_120872_);
            }
         }
      }
   }

   public static WorldCoordinate parseInt(StringReader p_120870_) throws CommandSyntaxException {
      if (p_120870_.canRead() && p_120870_.peek() == '^') {
         throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(p_120870_);
      } else if (!p_120870_.canRead()) {
         throw ERROR_EXPECTED_INT.createWithContext(p_120870_);
      } else {
         boolean flag = isRelative(p_120870_);
         int valueStart = p_120870_.getCursor();
         String token = p_120870_.canRead() && p_120870_.peek() != ' ' ? readCoordinateToken(p_120870_) : "";
         try {
            String literal = token.isEmpty() ? "0" : token;
            double d0 = token.isEmpty() ? 0.0D : (flag ? Double.parseDouble(token) : Long.parseLong(token));
            return new WorldCoordinate(flag, d0, literal);
         } catch (NumberFormatException exception) {
            p_120870_.setCursor(valueStart);
            throw ERROR_EXPECTED_INT.createWithContext(p_120870_);
         }
      }
   }

   private static String readCoordinateToken(StringReader reader) {
      int start = reader.getCursor();
      while (reader.canRead() && reader.peek() != ' ') reader.skip();
      return reader.getString().substring(start, reader.getCursor());
   }

   private static boolean isDecimal(String token) {
      try {
         new BigDecimal(token);
         return true;
      } catch (NumberFormatException exception) {
         return false;
      }
   }

   public static boolean isRelative(StringReader p_120875_) {
      boolean flag;
      if (p_120875_.peek() == '~') {
         flag = true;
         p_120875_.skip();
      } else {
         flag = false;
      }

      return flag;
   }

   public boolean equals(Object p_120877_) {
      if (this == p_120877_) {
         return true;
      } else if (!(p_120877_ instanceof WorldCoordinate)) {
         return false;
      } else {
         WorldCoordinate worldcoordinate = (WorldCoordinate)p_120877_;
         if (this.relative != worldcoordinate.relative) {
            return false;
         } else {
            return Double.compare(worldcoordinate.value, this.value) == 0;
         }
      }
   }

   public int hashCode() {
      int i = this.relative ? 1 : 0;
      long j = Double.doubleToLongBits(this.value);
      return 31 * i + (int)(j ^ j >>> 32);
   }

   public boolean isRelative() {
      return this.relative;
   }

   public static WorldCoordinate absoluteDecimal(String value, boolean centerCorrect) {
      if (!isDecimal(value)) throw new IllegalArgumentException("Invalid coordinate: " + value);
      BigDecimal decimal = new BigDecimal(value);
      if (centerCorrect && value.indexOf('.') < 0) decimal = decimal.add(BigDecimal.valueOf(0.5D));
      return new WorldCoordinate(false, decimal.doubleValue(), decimal.toPlainString());
   }
}